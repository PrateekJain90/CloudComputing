/**
 * Most of the instance creation related code has been referenced 
 * from the AWS SDK for Java example
 */

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.DeleteLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.DeletePolicyRequest;
import com.amazonaws.services.autoscaling.model.InstanceMonitoring;
import com.amazonaws.services.autoscaling.model.LaunchConfiguration;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.ComparisonOperator;
import com.amazonaws.services.cloudwatch.model.DeleteAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.cloudwatch.model.Statistic;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupEgressRequest;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CancelSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.LaunchSpecification;
import com.amazonaws.services.ec2.model.RequestSpotInstancesRequest;
import com.amazonaws.services.ec2.model.RequestSpotInstancesResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.AddTagsRequest;
import com.amazonaws.services.elasticloadbalancing.model.ApplySecurityGroupsToLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.CrossZoneLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck;
import com.amazonaws.services.elasticloadbalancing.model.Listener;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerAttributes;
import com.amazonaws.services.elasticloadbalancing.model.ModifyLoadBalancerAttributesRequest;
import com.amazonaws.services.elasticloadbalancing.model.Policies;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.Math;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URLEncoder;

import org.apache.commons.io.IOUtils;
import org.ini4j.Ini;
import org.ini4j.InvalidFileFormatException;
import org.ini4j.Profile.Section;
import org.omg.CORBA.RepositoryIdHelper;

public class ProjectTwo {
    
    public static AmazonEC2 ec2 = null;
    public static AmazonElasticLoadBalancingClient elbClient = null;
    public static AmazonAutoScalingClient scaleClient = null;
    public static AmazonCloudWatchClient watchClient = null;
    public static String scaleOut = null;
    public static String scaleIn = null;
	
    public static String performGetRequest(String urlString) throws IOException{
  
    	StringBuffer response = new StringBuffer();
        URLConnection conn = null;
        InputStream is = null;
        String errorResponse = "NA";

         try {
             URL url = new URL(urlString);
             conn = url.openConnection();
             
             BufferedReader br = new BufferedReader(
                                new InputStreamReader(conn.getInputStream()));
             String inputLine;
             
             while ((inputLine = br.readLine()) != null) {
            	 	 System.out.println(inputLine);
                     response.append(inputLine);
             }
             br.close();
             System.out.println("Done");
            

         } catch (Exception e) {
        	 
 			if (conn instanceof HttpURLConnection) {
    	        HttpURLConnection httpConn = (HttpURLConnection) conn;
    	        int statusCode = httpConn.getResponseCode();
    	        if (statusCode != 200) {
    	            is = httpConn.getErrorStream();
    	            errorResponse = IOUtils.toString(is);
    	        }
 			} 
         }
         
         
         return (response.length()>0)? response.toString() : errorResponse;
    }

    
    public static void sleep(long seconds)
    {
    	try {
    	    Thread.sleep(seconds * 1000);
    	} catch(InterruptedException ex) {
    	    Thread.currentThread().interrupt();
    	    System.out.println("Interrupted");
    	}
    }
    

    public static AWSCredentials awsCredentials(){
 
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider("default").getCredentials();
            System.out.println("Credentials configured");
        }catch (Exception e) {
            throw new AmazonClientException("Credentials issue",e);
        }
 
        return credentials;
    }
    
    public static void setUpEc2ClientOject(){
    	
        // Create the AmazonEC2Client object.
        ec2 = new AmazonEC2Client(awsCredentials());
        Region usEast1 = Region.getRegion(Regions.US_EAST_1);
        ec2.setRegion(usEast1);
        
        addSecurityGroup("SecurityGroupForLoadGenerator");
        addSecurityGroup("SecurityGroupForELBAndAutoscale");
    }
    
    public static void addSecurityGroup(String groupName){ 
 
        CreateSecurityGroupRequest csgr = new CreateSecurityGroupRequest();
        csgr.withGroupName(groupName).withDescription("Allow on all ports");
        
        ec2.createSecurityGroup(csgr);
        
        IpPermission ipPermission = new IpPermission();
        
        ipPermission.withIpRanges("0.0.0.0/0")
        	            .withIpProtocol("tcp")
        	            .withFromPort(0)
        	            .withToPort(65535);

        AuthorizeSecurityGroupIngressRequest incoming = new AuthorizeSecurityGroupIngressRequest();
        incoming.withGroupName(groupName).withIpPermissions(ipPermission);

        ec2.authorizeSecurityGroupIngress(incoming);
                
        System.out.println("Security Group " + groupName + " added");
    }
    
    public static String setUpSpotInstanceWithImage(String image)
    {
        System.out.println("Putting spot requet for image: " + image);
    	
        RequestSpotInstancesRequest loadGeneratorRequest = new RequestSpotInstancesRequest();

        loadGeneratorRequest.setSpotPrice("0.03");
        loadGeneratorRequest.setInstanceCount(Integer.valueOf(1));
        
        LaunchSpecification loadGeneratorSpecification = new LaunchSpecification();
        loadGeneratorSpecification.setImageId(image);
        loadGeneratorSpecification.setInstanceType("m3.medium");

        ArrayList<String> securityGroups = new ArrayList<String>();
        securityGroups.add("SecurityGroupForLoadGenerator");
        loadGeneratorSpecification.setSecurityGroups(securityGroups);
        
        loadGeneratorRequest.setLaunchSpecification(loadGeneratorSpecification);

        RequestSpotInstancesResult requestResult = ec2.requestSpotInstances(loadGeneratorRequest);
        List<SpotInstanceRequest> requestResponses = requestResult.getSpotInstanceRequests();
        
        String requestId = requestResponses.get(0).getSpotInstanceRequestId();
        
        ArrayList<String> spotInstanceRequestIds = new ArrayList<String>();
        spotInstanceRequestIds.add(requestId);	
        
        System.out.println("Finding Instance ID..");
        
        boolean anyOpen;
        
        ArrayList<String> instanceIds = new ArrayList<String>();

        do {
            DescribeSpotInstanceRequestsRequest describeRequest = new DescribeSpotInstanceRequestsRequest();
            describeRequest.setSpotInstanceRequestIds(spotInstanceRequestIds);

            anyOpen=false;

            try {
                DescribeSpotInstanceRequestsResult describeResult = ec2.describeSpotInstanceRequests(describeRequest);
                List<SpotInstanceRequest> describeResponses = describeResult.getSpotInstanceRequests();

                for (SpotInstanceRequest describeResponse : describeResponses) {
                        if (describeResponse.getState().equals("open")) {
                            anyOpen = true;
                            break;
                        }

                        instanceIds.add(describeResponse.getInstanceId());
                }
            } catch (AmazonServiceException e) {
                anyOpen = true;
            }
            
            sleep(30);
        } while (anyOpen);
        
        return instanceIds.get(0);
    }
    
    public static String getPublicDns(String instanceId) {
        
        System.out.println("Retreiving public dns.");
    	
    	DescribeInstancesResult results = ec2.describeInstances();
        List<Reservation> reservations = results.getReservations();

        for (Reservation reservation : reservations) {
          for (Instance instance : reservation.getInstances()) {
            if (instance.getInstanceId().equals(instanceId))
              return instance.getPublicDnsName();
          }
        }
        return "NA";
    }
    
    public static void addTagToInstanceID(String instanceID){

        System.out.println("Adding tags to the new instance");
    	
    	 ArrayList<Tag> instanceTags = new ArrayList<Tag>();
    	 instanceTags.add(new Tag("Project","2.1"));
    	 
         ArrayList<String> instanceIds = new ArrayList<String>();
         instanceIds.add(instanceID);
    	
    	 CreateTagsRequest request = new CreateTagsRequest();
    	 request.setResources(instanceIds);
    	 request.setTags(instanceTags);

    	 try{ 
    	 ec2.createTags(request);
    	 System.out.println("Added tag for image");
    	 }
    	 catch (AmazonServiceException e){ 
    		 System.out.println("Exception while adding tag" + e);
    	 }    	 
    }
    
    public static String setUpElbWithLoadGenerator(String loadGenerator) throws Exception{
    	
        String loadBalancerName = "projecttwoelb";
        
        // Create the ELB client.
        elbClient = new AmazonElasticLoadBalancingClient(awsCredentials());
        Region usEast1 = Region.getRegion(Regions.US_EAST_1);
        elbClient.setRegion(usEast1);
                        
                
        CreateLoadBalancerRequest elbRequest = new CreateLoadBalancerRequest();
        elbRequest.setLoadBalancerName(loadBalancerName);
        
        //Security Group
        
        DescribeSecurityGroupsRequest sgRequest = new DescribeSecurityGroupsRequest();
        ArrayList<String> list = new ArrayList<String>();
        list.add("SecurityGroupForELBAndAutoscale");
        sgRequest.setGroupNames(list);
        
        DescribeSecurityGroupsResult result = ec2.describeSecurityGroups(sgRequest);
        List<SecurityGroup> groups = result.getSecurityGroups();
        
        ArrayList<String> securityGroups = new ArrayList<String>();
        securityGroups.add(groups.get(0).getGroupId());
        elbRequest.setSecurityGroups(securityGroups);
        
        
        //Adding tags
        Collection<com.amazonaws.services.elasticloadbalancing.model.Tag> tags = 
        		new ArrayList<com.amazonaws.services.elasticloadbalancing.model.Tag>();
        
        com.amazonaws.services.elasticloadbalancing.model.Tag tag = new 
        		com.amazonaws.services.elasticloadbalancing.model.Tag();
        tag.setKey("Project");
        tag.setValue("2.1");
        tags.add(tag);
        
        AddTagsRequest tagReq = new AddTagsRequest();
        tagReq.withTags(tags);        
        elbRequest.setTags(tags);

        //Specify ports
        List<Listener> listener = new ArrayList<Listener>(1);
        listener.add(new Listener("HTTP", 80, 80));
        elbRequest.setListeners(listener);
 
        //Speciy availability zones
        List<String> zones = new ArrayList<String>(1);
        zones.add("us-east-1c");
        elbRequest.setAvailabilityZones(zones);
                
        CreateLoadBalancerResult lbResult=elbClient.createLoadBalancer(elbRequest);
        
        //Disable Cross Zone
        ModifyLoadBalancerAttributesRequest attribRequest = new ModifyLoadBalancerAttributesRequest();
        LoadBalancerAttributes attribs = new LoadBalancerAttributes();
        CrossZoneLoadBalancing crossZone = new CrossZoneLoadBalancing();
        crossZone.setEnabled(false);
        attribs.setCrossZoneLoadBalancing(crossZone);
        attribRequest.setLoadBalancerAttributes(attribs);
        attribRequest.setLoadBalancerName(loadBalancerName);
        elbClient.modifyLoadBalancerAttributes(attribRequest);
        
        //Specify Health Check
        String target = "HTTP:80/heartbeat?lg=" + URLEncoder.encode(loadGenerator,"UTF-8");
        ConfigureHealthCheckRequest healthCheckRequest = new ConfigureHealthCheckRequest();
        HealthCheck healthCheck = new HealthCheck(target, 5, 2, 2, 10);        
        healthCheckRequest.setHealthCheck(healthCheck);
        healthCheckRequest.setLoadBalancerName(loadBalancerName);
        elbClient.configureHealthCheck(healthCheckRequest);
        
        System.out.println("Load balancer created with name: " + lbResult.getDNSName());
        
        return lbResult.getDNSName();
    }
        
    
    public static void setUpLaunchConfiguration(){
    	
    	scaleClient = new AmazonAutoScalingClient(awsCredentials());
    	
        CreateLaunchConfigurationRequest launchConfiguration = new CreateLaunchConfigurationRequest();
        launchConfiguration.setImageId("ami-349fbb5e");
        launchConfiguration.setInstanceType("m3.medium");
    	launchConfiguration.setLaunchConfigurationName("ProjectTwoLaunchConfiguration");
        
        ArrayList<String> securityGroups = new ArrayList<String>();
        securityGroups.add("SecurityGroupForELBAndAutoscale");
        launchConfiguration.setSecurityGroups(securityGroups);
                
        launchConfiguration.setSpotPrice("0.03");
                
        scaleClient.createLaunchConfiguration(launchConfiguration);
        
        System.out.println("Launch configuration successful..");
    }
    
    
    public static void setUpAutoScaling(){
    	
        System.out.println("Setting up autoscale group..");    	
    	
    	CreateAutoScalingGroupRequest scaleRequest = new CreateAutoScalingGroupRequest();

    	scaleRequest.setAutoScalingGroupName("ProjectTwoAutoScaleGroup");
    	scaleRequest.setLaunchConfigurationName("ProjectTwoLaunchConfiguration");

    	//Start with one instance
        scaleRequest.setMinSize(1);
        scaleRequest.setMaxSize(10);

    	//Same zone
        List<String> zones = new ArrayList<String>(1);
        zones.add("us-east-1c");
        scaleRequest.setAvailabilityZones(zones);

        //Choose ELB
        List<String> loadBalancerName = new ArrayList<String>();
        loadBalancerName.add("projecttwoelb");
        scaleRequest.setLoadBalancerNames(loadBalancerName);

        scaleRequest.setHealthCheckType("ELB");
        scaleRequest.setHealthCheckGracePeriod(100);
        scaleRequest.setDefaultCooldown(60);
        
        //Adding tags
        Collection<com.amazonaws.services.autoscaling.model.Tag> tags = 
        		new ArrayList<com.amazonaws.services.autoscaling.model.Tag>();
        
        com.amazonaws.services.autoscaling.model.Tag tag = new com.amazonaws.services.autoscaling.model.Tag();
        tag.setKey("Project");
        tag.setValue("2.1");
   	 	tags.add(tag);
   	 	
        scaleRequest.setTags(tags);
        
        scaleClient.createAutoScalingGroup(scaleRequest);    	
       
        System.out.println("Autoscale group set up successfully..");    	
        System.out.println("Setting up autoscale policies..");    	
        
        setUpPolicies();
    }
    
    public static void setUpPolicies(){
    	
    	//Create policy request
    	PutScalingPolicyRequest policyRequest = new PutScalingPolicyRequest();
        policyRequest.setAutoScalingGroupName("ProjectTwoAutoScaleGroup");
        
        policyRequest.setPolicyName("ScaleOutInstances"); 
        policyRequest.setScalingAdjustment(1);
        policyRequest.setAdjustmentType("ChangeInCapacity");
        scaleOut = scaleClient.putScalingPolicy(policyRequest).getPolicyARN();
        
        policyRequest.setPolicyName("ScaleInInstances"); 
        policyRequest.setScalingAdjustment(-1);
        policyRequest.setAdjustmentType("ChangeInCapacity");
        scaleIn = scaleClient.putScalingPolicy(policyRequest).getPolicyARN();    
        
        System.out.println("Polices Set successfully..");    	
}
    
    
    public static void createCloudWatchPolicies(){
    	
        System.out.println("Preparing Alarms..");    	
    	
    	watchClient = new AmazonCloudWatchClient(awsCredentials());
    	
    	//Scale out alarm
    	PutMetricAlarmRequest scaleOutRequest = new PutMetricAlarmRequest();
        scaleOutRequest.setAlarmName("ScaleOutAlarm");
        scaleOutRequest.setMetricName("CPUUtilization");
        
        List<Dimension> dimensions = new ArrayList<Dimension>();
        Dimension dimension = new Dimension();
        dimension.setName("AutoScalingGroupName");
        dimension.setValue("ProjectTwoAutoScaleGroup");
        dimensions.add(dimension);
        scaleOutRequest.setDimensions(dimensions);

        scaleOutRequest.setNamespace("AWS/EC2");
        scaleOutRequest.setComparisonOperator(ComparisonOperator.GreaterThanThreshold);
        scaleOutRequest.setStatistic(Statistic.Average);
        scaleOutRequest.setUnit(StandardUnit.Percent);
        scaleOutRequest.setThreshold(70d);
        scaleOutRequest.setPeriod(60);
        scaleOutRequest.setEvaluationPeriods(1);

        //Linking alarm to policy
        List<String> actions = new ArrayList<String>();
        actions.add(scaleOut);
        scaleOutRequest.setAlarmActions(actions);
        
    	//Scale in alarm
    	PutMetricAlarmRequest scaleInRequest = new PutMetricAlarmRequest();
    	scaleInRequest.setAlarmName("ScaleInAlarm");
    	scaleInRequest.setMetricName("CPUUtilization");

    	scaleInRequest.setDimensions(dimensions);

        scaleInRequest.setNamespace("AWS/EC2");
        scaleInRequest.setComparisonOperator(ComparisonOperator.LessThanThreshold);
        scaleInRequest.setStatistic(Statistic.Average);
        scaleInRequest.setUnit(StandardUnit.Percent);
        scaleInRequest.setThreshold(20d);
        scaleInRequest.setPeriod(60);
        scaleInRequest.setEvaluationPeriods(4);

        //Linking alarm to policy
        List<String> actionsTwo = new ArrayList<String>();
        actionsTwo.add(scaleIn);
        scaleInRequest.setAlarmActions(actionsTwo);

        watchClient.putMetricAlarm(scaleOutRequest);
        watchClient.putMetricAlarm(scaleInRequest);
        
        System.out.println("Alarms set successfully..");    	
    }
    
    public static void deleteSecurityGroup(){
    	
    	DeleteSecurityGroupRequest request = new DeleteSecurityGroupRequest();
    	request.setGroupName("SecurityGroupForELBAndAutoscale");
       	ec2.deleteSecurityGroup(request);
    }
    
    public static void deleteElasticLoadBalancer(){
    	DeleteLoadBalancerRequest request = new DeleteLoadBalancerRequest();
    	request.setLoadBalancerName("projecttwoelb");
    	elbClient.deleteLoadBalancer(request);
    }
    
    public static void deleteLaunchConfiguration(){    	
    	DeleteLaunchConfigurationRequest request = new DeleteLaunchConfigurationRequest();
    	request.setLaunchConfigurationName("ProjectTwoLaunchConfiguration");
    	scaleClient.deleteLaunchConfiguration(request);
    }
    
    public static void deleteAutoScalingGroup(){
    	
    	DeleteAutoScalingGroupRequest request = new DeleteAutoScalingGroupRequest();
    	request.setAutoScalingGroupName("ProjectTwoAutoScaleGroup");
    	request.setForceDelete(true);
    	scaleClient.deleteAutoScalingGroup(request);
    }
    
    public static void deletePolicies(){
    	
    	DeletePolicyRequest request = new DeletePolicyRequest();
    	request.setAutoScalingGroupName("ProjectTwoAutoScaleGroup");
    	request.setPolicyName("ScaleInInstances");  	
    	scaleClient.deletePolicy(request);
    	request.setPolicyName("ScaleOutInstances");
      	scaleClient.deletePolicy(request);
    }
    
    public static void deleteAlarms(){
    
    	DeleteAlarmsRequest request = new DeleteAlarmsRequest();
    	ArrayList<String> alarms = new ArrayList<String>();
    	alarms.add("ScaleOutAlarm");
    	alarms.add("ScaleInAlarm");
    	request.setAlarmNames(alarms);
    	
    	watchClient.deleteAlarms(request);
    }
    

    public static void main(String[] args) throws Exception {

        setUpEc2ClientOject();
        
        //Prepare Load Generator
        String loadGenID = setUpSpotInstanceWithImage("ami-8ac4e9e0");
        addTagToInstanceID(loadGenID);
        String loadGeneratorVmName = getPublicDns(loadGenID);

        System.out.println("DNS of loadGenerator: " + loadGeneratorVmName);
        
		//Create and Elastic Load Balancer
        setUpElbWithLoadGenerator(loadGeneratorVmName);
        
        setUpLaunchConfiguration();
        
        setUpAutoScaling();
        
        createCloudWatchPolicies();
        
        //Let one of the instance start
        sleep(300);
        
        String launchTest = "http://ec2-52-90-144-152.compute-1.amazonaws.com/junior?dns=" 
        												+ URLEncoder.encode(dnsName,"UTF-8");
        
        String response = performGetRequest(launchTest);
        System.out.println("Response is: " + response);
        
      while(response.contains("Invalid Elastic Load Balancer DNS"))
      {
    	  response = performGetRequest(launchTest);
    	  sleep(1);
      }
        
      //Wait for the test to end
      sleep(3000);  
      
      
      deleteAlarms();
      deletePolicies();
      deleteAutoScalingGroup();
      deleteLaunchConfiguration();
      deleteElasticLoadBalancer();

      //Give time for instances to terminate
      sleep(60);
      deleteSecurityGroup();
      
      System.out.println("Deleted all resources");
    }
}
