package org.example;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    public static void startInstance(String instance_id)
    {
        DryRunSupportedRequest<StartInstancesRequest> dry_request =
                () -> {
                    StartInstancesRequest request = new StartInstancesRequest()
                            .withInstanceIds(instance_id);

                    return request.getDryRunRequest();
                };

        DryRunResult dry_response = ec2.dryRun(dry_request);

        if(!dry_response.isSuccessful()) {
            System.out.printf(
                    "Failed dry run to start instance %s", instance_id);

            throw dry_response.getDryRunResponse();
        }

        StartInstancesRequest request = new StartInstancesRequest()
                .withInstanceIds(instance_id);

        ec2.startInstances(request);

        System.out.printf("Successfully started instance %s", instance_id);
    }

    public static void stopInstance(String instance_id)
    {
        DryRunSupportedRequest<StopInstancesRequest> dry_request =
                () -> {
                    StopInstancesRequest request = new StopInstancesRequest()
                            .withInstanceIds(instance_id);

                    return request.getDryRunRequest();
                };

        DryRunResult dry_response = ec2.dryRun(dry_request);

        if(!dry_response.isSuccessful()) {
            System.out.printf(
                    "Failed dry run to stop instance %s", instance_id);
            throw dry_response.getDryRunResponse();
        }

        StopInstancesRequest request = new StopInstancesRequest()
                .withInstanceIds(instance_id);

        ec2.stopInstances(request);

        System.out.printf("Successfully stop instance %s", instance_id);
    }

    public static void createInstance(String name, String amiId, String subnetId){
        RunInstancesRequest run_request = new RunInstancesRequest()
                .withImageId(amiId)
                .withSubnetId(subnetId)
                .withInstanceType(InstanceType.T2Micro)
                .withMaxCount(1)
                .withMinCount(1);

        RunInstancesResult run_response = ec2.runInstances(run_request);

        String reservation_id = run_response.getReservation().getInstances().get(0).getInstanceId();

        Tag tag = new Tag()
                .withKey("Name")
                .withValue(name);

        CreateTagsRequest tag_request = new CreateTagsRequest()
                .withResources(reservation_id)
                .withTags(tag);

        ec2.createTags(tag_request);

        System.out.printf(
                "Successfully started EC2 instance %s based on AMI %s",
                reservation_id, amiId);
    }

    public static void createVpc(String name){
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

    public static void createSubnet(String name, String vpcId, String cidr, String availabilityZone){
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
    }

    public static void main( String[] args )
    {
        // Start/Stop Instance
//        boolean start = false;
//        String instanceId = "i-05820cfc84498430a";
//        if(start) {
//            startInstance(instanceId);
//        } else {
//            stopInstance(instanceId);
//        }


        // Create instance
//        createInstance("test", "amiId", "subnetId");

        // Create Vpc with tag
//        createVpc("test");


        // Set VPC tags
//        List<Tag> tagList = Arrays.asList(new Tag("Name", "changed"), new Tag("Owner", "Kaushal"), new Tag("Owner 2", "sumit"));
//        setVpcTag(tagList, "vpc-0e748fca053aa1ffd");


        // Delete VPC Tags
//        List<Tag> tagList = Arrays.asList(new Tag("Owner"), new Tag("Owner 2"));
//        deleteVpcTag(tagList, "vpc-0e748fca053aa1ffd");

        // Create Subnet
//        createSubnet("Test", "vpc-0e748fca053aa1ffd", "10.0.0.0/27", "us-east-1b");
    }
}
