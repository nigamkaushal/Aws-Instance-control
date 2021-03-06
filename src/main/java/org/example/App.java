package org.example;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancingv2.model.*;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class App
{
    static File awsCredentialsFile=new File("src/main/java/org/example/credFile.txt");
    static AWSCredentials credentials;

    static {
        try {
            credentials = new PropertiesCredentials(awsCredentialsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static final AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard().withRegion(Regions.US_EAST_1)
            .withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

    static final AmazonElasticLoadBalancing elb = AmazonElasticLoadBalancingClientBuilder.standard().withRegion(Regions.US_EAST_1)
            .withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

    public static void startInstance(String instanceId)
    {
        DryRunSupportedRequest<StartInstancesRequest> dryRequest =
                () -> {
                    StartInstancesRequest request = new StartInstancesRequest()
                            .withInstanceIds(instanceId);

                    return request.getDryRunRequest();
                };

        DryRunResult<StartInstancesRequest> dryResponse = ec2.dryRun(dryRequest);

        if(!dryResponse.isSuccessful()) {
            System.out.printf(
                    "Failed dry run to start instance %s", instanceId);

            throw dryResponse.getDryRunResponse();
        }

        StartInstancesRequest request = new StartInstancesRequest()
                .withInstanceIds(instanceId);

        ec2.startInstances(request);

        System.out.printf("Successfully started instance %s", instanceId);
    }

    public static void stopInstance(String instanceId)
    {
        DryRunSupportedRequest<StopInstancesRequest> dryRequest =
                () -> {
                    StopInstancesRequest request = new StopInstancesRequest()
                            .withInstanceIds(instanceId);

                    return request.getDryRunRequest();
                };

        DryRunResult<StopInstancesRequest> dryResponse = ec2.dryRun(dryRequest);

        if(!dryResponse.isSuccessful()) {
            System.out.printf(
                    "Failed dry run to stop instance %s", instanceId);
            throw dryResponse.getDryRunResponse();
        }

        StopInstancesRequest request = new StopInstancesRequest()
                .withInstanceIds(instanceId);

        ec2.stopInstances(request);

        System.out.printf("Successfully stop instance %s", instanceId);
    }

    public static void createKeyPair(String keyName){
        CreateKeyPairRequest createKeyPairRequest = new CreateKeyPairRequest()
                .withKeyName(keyName);
        CreateKeyPairResult createKeyPairResult = ec2.createKeyPair(createKeyPairRequest);
        KeyPair keyPair = createKeyPairResult.getKeyPair();
        String privateKey = keyPair.getKeyMaterial();
        File file = new File(keyName + ".pem");
        try (FileWriter fw = new FileWriter(file)){
            fw.append(privateKey);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteKeyPair(String keyName){
        DeleteKeyPairRequest deleteKeyPairRequest = new DeleteKeyPairRequest()
                .withKeyName(keyName);
        ec2.deleteKeyPair(deleteKeyPairRequest);
        try {
            Files.delete(Path.of(keyName + ".pem"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String createInstance(String name, String amiId, String subnetId, String keyName){
        InstanceNetworkInterfaceSpecification interfaceSpecification = new InstanceNetworkInterfaceSpecification()
                .withDeviceIndex(0)
                .withAssociatePublicIpAddress(true)
                .withSubnetId(subnetId);
//        String data = "#!/bin/bash \n" + "amazon-linux-extras install nginx \n" + "systemctl start nginx \n";
//        String base64Data = null;
//        try {
//            base64Data = new String(Base64.encode(data.getBytes(StandardCharsets.UTF_8)), "UTF-8");
//        } catch (UnsupportedEncodingException e) {
//            throw new RuntimeException(e);
//        }

        RunInstancesRequest runRequest = new RunInstancesRequest()
                .withImageId(amiId)
                .withInstanceType(InstanceType.T2Micro)
                .withMaxCount(1)
                .withMinCount(1)
                .withNetworkInterfaces(interfaceSpecification)
                .withKeyName(keyName);
//                .withUserData(base64Data);

        RunInstancesResult runResponse = ec2.runInstances(runRequest);
        String reservationId = runResponse.getReservation().getInstances().get(0).getInstanceId();

        Tag tag = new Tag()
                .withKey("Name")
                .withValue(name);

        CreateTagsRequest tagRequest = new CreateTagsRequest()
                .withResources(reservationId)
                .withTags(tag);

        ec2.createTags(tagRequest);

        System.out.printf(
                "Successfully started EC2 instance %s based on AMI %s %n",
                reservationId, amiId);
        return  reservationId;
    }

    public static void terminateInstance(String instanceId){
        TerminateInstancesRequest instancesRequest = new TerminateInstancesRequest()
                .withInstanceIds(instanceId);
        ec2.terminateInstances(instancesRequest);
    }

    public static String createVpc(String name){
        Tag tag = new Tag()
            .withKey("Name")
            .withValue(name);
        List<Tag> tagList = new ArrayList<>();
        tagList.add(tag);
        ResourceType resourceType = ResourceType.Vpc;
        TagSpecification tagSpecification = new TagSpecification();
        tagSpecification.setTags(tagList);
        tagSpecification.setResourceType(resourceType);
        CreateVpcRequest vpcRequest = new CreateVpcRequest("10.0.0.0/16")
                .withTagSpecifications(tagSpecification);
        CreateVpcResult vpcResult = ec2.createVpc(vpcRequest);
        System.out.println(vpcResult.getVpc().toString());
        return vpcResult.getVpc().getVpcId();
    }

    public static void deleteVpc(String vpcId){
        DeleteVpcRequest deleteVpcRequest = new DeleteVpcRequest()
                .withVpcId(vpcId);
        ec2.deleteVpc(deleteVpcRequest);
    }

    public static void setVpcTag(List<Tag> tagList, String vpcId){
        CreateTagsRequest tagsRequest = new CreateTagsRequest().withResources(vpcId);
        tagsRequest.withTags(tagList);
        ec2.createTags(tagsRequest);
    }

    public static void deleteVpcTag(List<Tag> tagList, String vpcId){
        DeleteTagsRequest tagsRequest = new DeleteTagsRequest().withResources(vpcId);
        tagsRequest.withTags(tagList);
        ec2.deleteTags(tagsRequest);
    }

    public static String createSubnet(String name, String vpcId, String cidr, String availabilityZone){
        Tag tag = new Tag()
                .withKey("Name")
                .withValue(name);
        ResourceType resourceType = ResourceType.Subnet;
        TagSpecification tagSpecification = new TagSpecification();
        tagSpecification.withTags(tag);
        tagSpecification.setResourceType(resourceType);
        CreateSubnetRequest subnetRequest = new CreateSubnetRequest(vpcId, cidr)
                .withAvailabilityZone(availabilityZone)
                .withTagSpecifications(tagSpecification);

        CreateSubnetResult subnetResult = ec2.createSubnet(subnetRequest);
        System.out.println(subnetResult.toString());
        return subnetResult.getSubnet().getSubnetId();
    }

    public static String createIGW(String name, String vpcId){
        Tag tag = new Tag()
                .withKey("Name")
                .withValue(name);
        ResourceType resourceType = ResourceType.InternetGateway;
        TagSpecification tagSpecification = new TagSpecification();
        tagSpecification.withTags(tag);
        tagSpecification.setResourceType(resourceType);
        CreateInternetGatewayRequest request = new CreateInternetGatewayRequest()
                .withTagSpecifications(tagSpecification);

        CreateInternetGatewayResult result = ec2.createInternetGateway(request);
        System.out.println(result.toString());
        AttachInternetGatewayRequest attachInternetGatewayRequest = new AttachInternetGatewayRequest()
                .withInternetGatewayId(result.getInternetGateway().getInternetGatewayId())
                .withVpcId(vpcId);
        ec2.attachInternetGateway(attachInternetGatewayRequest);
        return result.getInternetGateway().getInternetGatewayId();
    }

    public static void createRouteTableWithIGW(String name, String vpcId, String subnetId, String igwId){
        Tag tag = new Tag()
                .withKey("Name")
                .withValue(name);
        ResourceType resourceType = ResourceType.RouteTable;
        TagSpecification tagSpecification = new TagSpecification();
        tagSpecification.withTags(tag);
        tagSpecification.setResourceType(resourceType);
        CreateRouteTableRequest request = new CreateRouteTableRequest()
                .withTagSpecifications(tagSpecification)
                .withVpcId(vpcId);
        CreateRouteTableResult result = ec2.createRouteTable(request);
        System.out.println(result.toString());
        AssociateRouteTableRequest associateRouteTableRequest = new AssociateRouteTableRequest()
                .withRouteTableId(result.getRouteTable().getRouteTableId())
                .withSubnetId(subnetId);
        ec2.associateRouteTable(associateRouteTableRequest);
        CreateRouteRequest createRouteRequest = new CreateRouteRequest()
                .withRouteTableId(result.getRouteTable().getRouteTableId())
                .withDestinationCidrBlock("0.0.0.0/0")
                .withGatewayId(igwId);
        ec2.createRoute(createRouteRequest);
    }

    public static void createRouteTableWithNGW(String name, String vpcId, String subnetId, String ngwId){
        Tag tag = new Tag()
                .withKey("Name")
                .withValue(name);
        ResourceType resourceType = ResourceType.RouteTable;
        TagSpecification tagSpecification = new TagSpecification();
        tagSpecification.withTags(tag);
        tagSpecification.setResourceType(resourceType);
        CreateRouteTableRequest request = new CreateRouteTableRequest()
                .withTagSpecifications(tagSpecification)
                .withVpcId(vpcId);
        CreateRouteTableResult result = ec2.createRouteTable(request);
        System.out.println(result.toString());
        AssociateRouteTableRequest associateRouteTableRequest = new AssociateRouteTableRequest()
                .withRouteTableId(result.getRouteTable().getRouteTableId())
                .withSubnetId(subnetId);
        ec2.associateRouteTable(associateRouteTableRequest);
        CreateRouteRequest createRouteRequest = new CreateRouteRequest()
                .withRouteTableId(result.getRouteTable().getRouteTableId())
                .withDestinationCidrBlock("0.0.0.0/0")
                .withNatGatewayId(ngwId);
        ec2.createRoute(createRouteRequest);
    }

    public static void allocateElasticIP(String elasticIP, String instanceId){

        AssociateAddressRequest associateAddressRequest =
                new AssociateAddressRequest()
                        .withInstanceId(instanceId)
                        .withPublicIp(elasticIP);

        AssociateAddressResult associateAddressResult =
                ec2.associateAddress(associateAddressRequest);
        System.out.println(associateAddressResult.toString());
    }

    public static String createElasticAddress(String name){
        Tag tag = new Tag()
                .withKey("Name")
                .withValue(name);
        ResourceType resourceType = ResourceType.ElasticIp;
        TagSpecification tagSpecification = new TagSpecification();
        tagSpecification.withTags(tag);
        tagSpecification.setResourceType(resourceType);
        AllocateAddressRequest allocateAddressRequest = new AllocateAddressRequest()
                .withTagSpecifications(tagSpecification);
        AllocateAddressResult allocateAddressResult = ec2.allocateAddress(allocateAddressRequest);
        System.out.println(allocateAddressResult.toString());
        return allocateAddressResult.getAllocationId();
    }

    public static String createNATGateway(String name, String subnetId, String allocationId){
        Tag tag = new Tag()
                .withKey("Name")
                .withValue(name);
        ResourceType resourceType = ResourceType.Natgateway;
        TagSpecification tagSpecification = new TagSpecification();
        tagSpecification.withTags(tag);
        tagSpecification.setResourceType(resourceType);
        CreateNatGatewayRequest createNatGatewayRequest = new CreateNatGatewayRequest()
                .withTagSpecifications(tagSpecification)
                .withSubnetId(subnetId)
                .withConnectivityType(ConnectivityType.Public)
                .withAllocationId(allocationId);
        CreateNatGatewayResult result =  ec2.createNatGateway(createNatGatewayRequest);
        System.out.println(result);
        return result.getNatGateway().getNatGatewayId();
    }

    public static String createSecurityGroup(){
        String myIp = null;
        try{
            URL url = new URL("http://checkip.amazonaws.com/");
            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            myIp = br.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        IpRange ipRange1 = new IpRange().withCidrIp(myIp+ "/32");
        IpPermission ipPermission = new IpPermission()
                .withIpv4Ranges(ipRange1)
                .withIpProtocol("tcp")
                .withFromPort(22)
                .withToPort(22);
        CreateSecurityGroupRequest securityGroupRequest = new CreateSecurityGroupRequest()
                .withGroupName("TestSG")
                .withDescription("Test SG");
        CreateSecurityGroupResult securityGroupResult = ec2.createSecurityGroup(securityGroupRequest);
        System.out.println(securityGroupResult.toString());

        AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest =
                new AuthorizeSecurityGroupIngressRequest()
                        .withGroupName("TestSG")
                        .withIpPermissions(ipPermission);
        ec2.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest);
        return securityGroupResult.getGroupId();
    }

    public static String createVolume(String name, int size, String az){
        Tag tag = new Tag()
                .withKey("Name")
                .withValue(name);
        ResourceType resourceType = ResourceType.Volume;
        TagSpecification tagSpecification = new TagSpecification();
        tagSpecification.withTags(tag);
        tagSpecification.setResourceType(resourceType);
        CreateVolumeRequest createVolumeRequest = new CreateVolumeRequest()
                .withTagSpecifications(tagSpecification)
                .withAvailabilityZone(az)
                .withVolumeType(VolumeType.Gp2)
                .withSize(size)
                .withEncrypted(false);
        CreateVolumeResult volumeResult = ec2.createVolume(createVolumeRequest);
        System.out.println(volumeResult.toString());
        return volumeResult.getVolume().getVolumeId();
    }

    public static void deleteVolume(String volumeId){
        DeleteVolumeRequest deleteVolumeRequest = new DeleteVolumeRequest()
                .withVolumeId(volumeId);
        ec2.deleteVolume(deleteVolumeRequest);
    }

    public static void modifyVolume(String volumeId, int size){
        ModifyVolumeRequest modifyVolumeRequest = new ModifyVolumeRequest()
                .withVolumeId(volumeId)
                .withSize(size);
        ec2.modifyVolume(modifyVolumeRequest);
    }

    public static void attachVolume(String volumeId, String instanceId, String device){
        AttachVolumeRequest attachVolumeRequest = new AttachVolumeRequest()
                .withVolumeId(volumeId)
                .withInstanceId(instanceId)
                .withDevice(device);
        ec2.attachVolume(attachVolumeRequest);
    }

    public static void detachVolume(String volumeId){
        DetachVolumeRequest detachVolumeRequest = new DetachVolumeRequest()
                .withVolumeId(volumeId)
                .withForce(true);
        ec2.detachVolume(detachVolumeRequest);
    }

    public static String createSnapshot(String name, String volumeId){
        Tag tag = new Tag()
                .withKey("Name")
                .withValue(name);
        ResourceType resourceType = ResourceType.Snapshot;
        TagSpecification tagSpecification = new TagSpecification();
        tagSpecification.withTags(tag);
        tagSpecification.setResourceType(resourceType);
        CreateSnapshotRequest createSnapshotRequest = new CreateSnapshotRequest()
                .withTagSpecifications(tagSpecification)
                .withVolumeId(volumeId)
                .withDescription("SnapShot of volume");
        CreateSnapshotResult createSnapshotResult = ec2.createSnapshot(createSnapshotRequest);
        System.out.println(createSnapshotResult.toString());
        DescribeSnapshotsRequest describeSnapshotsRequest = new DescribeSnapshotsRequest()
                .withSnapshotIds(createSnapshotResult.getSnapshot().getSnapshotId());
        Snapshot snapshot = null;
        do{
            snapshot = ec2.describeSnapshots(describeSnapshotsRequest).getSnapshots().get(0);
            if(snapshot.getState().equals("error")){
                System.out.println("Error creating snapshot.");
                break;
            }
            if(snapshot.getState().equals("completed")){
                System.out.println("Completed creating snapshot.");
            }
        }while (!snapshot.getState().equals("completed"));
        return createSnapshotResult.getSnapshot().getSnapshotId();
    }

    public static String createVolumeFromSnapshot(String name, String snapshotId, String az){
        Tag tag = new Tag()
                .withKey("Name")
                .withValue(name);
        ResourceType resourceType = ResourceType.Volume;
        TagSpecification tagSpecification = new TagSpecification();
        tagSpecification.withTags(tag);
        tagSpecification.setResourceType(resourceType);
        CreateVolumeRequest createVolumeRequest = new CreateVolumeRequest()
                .withTagSpecifications(tagSpecification)
                .withSnapshotId(snapshotId)
                .withAvailabilityZone(az);
        CreateVolumeResult volumeResult = ec2.createVolume(createVolumeRequest);
        System.out.println(volumeResult);
        return volumeResult.getVolume().getVolumeId();
    }

    public static String createTargetGroup(String name, String vpcId, String... instanceIds){
        CreateTargetGroupRequest createTargetGroupRequest = new CreateTargetGroupRequest()
                .withTargetType(TargetTypeEnum.Instance)
                .withName(name)
                .withTags(new com.amazonaws.services.elasticloadbalancingv2.model.Tag().withKey("Name").withValue(name))
                .withProtocol(ProtocolEnum.TCP)
                .withPort(80)
                .withVpcId(vpcId)
                .withHealthCheckProtocol(ProtocolEnum.TCP);
        CreateTargetGroupResult createTargetGroupResult = elb.createTargetGroup(createTargetGroupRequest);
        List<TargetDescription> targetDescriptions = new ArrayList<>();
        for(String instanceId : instanceIds)
            targetDescriptions.add(new TargetDescription().withId(instanceId));
        RegisterTargetsRequest registerTargetsRequest = new RegisterTargetsRequest()
                .withTargets(targetDescriptions)
                .withTargetGroupArn(createTargetGroupResult.getTargetGroups().get(0).getTargetGroupArn());
        elb.registerTargets(registerTargetsRequest);
        System.out.println(createTargetGroupResult);
        return createTargetGroupResult.getTargetGroups().toString();
    }

    public static void getSubnetsFromVpc(String vpcId){
        DescribeSubnetsRequest describeSubnetsRequest = new DescribeSubnetsRequest()
                .withFilters(new Filter().withName("vpc-id").withValues(vpcId));
        DescribeSubnetsResult describeSubnetsResult = ec2.describeSubnets(describeSubnetsRequest);
        System.out.println(describeSubnetsResult.getSubnets());
    }

    // Pending
    public static void createLoadBalancer(String name, LoadBalancerTypeEnum type, LoadBalancerSchemeEnum scheme){

        CreateLoadBalancerRequest createLoadBalancerRequest = new CreateLoadBalancerRequest()
                .withTags(new com.amazonaws.services.elasticloadbalancingv2.model.Tag().withKey("Name").withValue(name))
                .withName(name)
                .withType(type)
                .withScheme(scheme)
                .withSubnetMappings();
    }

    public static void main( String[] args )
    {
        // Start/Stop Instance
//        boolean start = false;
//        String instanceId = "instanceId";
//        if(start) {
//            startInstance(instanceId);
//        } else {
//            stopInstance(instanceId);
//        }

//        deleteKeyPair("keypair");

        // Create Keypair
//        createKeyPair("keypair");

        // Create Elastic IP
//        String elasticIP = createElasticAddress("testElastic");

        // Associate Elastic IP
//        allocateElasticIP(elasticIP, instanceId);

        // Create Vpc with tag
//        String vpcId = createVpc("testVPC");

        // Set VPC tags
//        List<Tag> tagList = Arrays.asList(new Tag("Name", "changed"), new Tag("Owner", "Kaushal"), new Tag("Owner 2", "sumit"));
//        setVpcTag(tagList, "vpcId");

        // Delete VPC Tags
//        List<Tag> tagList = Arrays.asList(new Tag("Owner"), new Tag("Owner 2"));
//        deleteVpcTag(tagList, "vpcId");

        // Create Subnet
//        String subnetId = createSubnet("TestSubnet", vpcId, "10.0.0.0/27", "us-east-1a");

        // Create IGW
//        String igwId = createIGW("testIGW", vpcId);

        // Create RouteTable with IGW
//        createRouteTableWithIGW("testRouteTable", vpcId, subnetId, igwId);

        // Create RouteTable with NGW
//        createRouteTableWithIGW("testRouteTable", vpcId, subnetId, natGId);

        // Create instance
//        String instanceId = createInstance("testInstance", "ami-0f9fc25dd2506cf6d", subnetId, "keypair");

//        String allocationId = createElasticAddress("testElastic");

        // Create NAT Gateway
//        String natGId = createNATGateway("testNAT", subnetId, allocationId);

        //For Sleep
//        try {
//            TimeUnit.SECONDS.sleep(190);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

        // endpoints pending

        // VOLUME
//        String volumeId = createVolume("testVolume", 50, "us-east-1a");
//        deleteVolume("vol-01d8e8933647c9cd8");
//        modifyVolume("vol-01d8e8933647c9cd8", 10);
//        try {
//            System.out.println("Waiting for 10 seconds...");
//            TimeUnit.SECONDS.sleep(10);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        attachVolume(volumeId, instanceId, "/dev/sdf");
//        detachVolume(volumeId);

//        String snapshotId = createSnapshot("testSnapshot", volumeId);

//        try {
//            System.out.println("Waiting for 30 seconds...");
//            TimeUnit.SECONDS.sleep(30);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

//        createVolumeFromSnapshot("VolumeSnapshot", "snap-052c9e7192e6d9f49","us-east-1a");


        // Create Target Group
//        createTargetGroup("testTargetGroup", "vpc-095d922694dfe4936", "i-0adbd2db81ca26eca");

        // Create Load Balancer
//        createLoadBalancer("TestLB", "arn:aws:elasticloadbalancing:us-east-1:141259127249:targetgroup/testTargetGroup/597755878d45d261")

    }
}
