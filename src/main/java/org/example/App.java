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

    final static AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard().withRegion(Regions.US_EAST_1)
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

        CreateTagsResult tag_response = ec2.createTags(tag_request);

        System.out.printf(
                "Successfully started EC2 instance %s based on AMI %s",
                reservation_id, amiId);
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
        createInstance("test", "amiId", "subnetId");
    }
}
