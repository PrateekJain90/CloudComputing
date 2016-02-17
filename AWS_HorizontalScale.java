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
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CancelSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.Math;
import java.net.URI;
import java.util.ArrayList;
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
	
    public static void printInitialInfo(String vhdURL, String vmName){    	
    }
    

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
    
    
    public static boolean downloadLogFile(String urlString){
    	
        URLConnection conn = null;
        InputStream is = null;
        FileOutputStream outputStream;
        boolean taskSuccessful = false;
         try {
             URL url = new URL(urlString);
             conn = url.openConnection();
          
             is = conn.getInputStream();
             outputStream = new FileOutputStream("log.ini");
             int bytesRead = -1;
             byte[] buffer = new byte[1024];
             
             while ((bytesRead = is.read(buffer)) != -1) {
            	 
            	 String s = new String(buffer);
            	 if(s.contains("Congratulation"))
            		 taskSuccessful = true;
                 outputStream.write(buffer, 0, bytesRead);
             }
             
             outputStream.close();
             is.close();             
             
             System.out.println("File downloaded");
            

         } catch (Exception e) {
        	 System.out.println(e);
         }
         
         return taskSuccessful;
    }
    
    
    
    public static String parseLogId(String log)
    {
    	String parsedLog = "Parse error";
    	
    	if(log == "NA")
    		return log;
    	
    	Pattern pattern = Pattern.compile("test\\.\\d*\\.log");
    	Matcher matcher = pattern.matcher(log);
    	if (matcher.find())
    	    parsedLog = matcher.group(0);

    	return parsedLog;
    }
    
    public static float calculateRpm() throws InvalidFileFormatException, FileNotFoundException, IOException
    {
        Ini ini = new Ini(new FileReader("log.ini"));
    	float rpm=0;
    	
    	if(ini.size()>31)
    		return -1;

    	System.out.println("Number of sections: "+ini.size()+"\n");
    	
    	int counter = 1;
    	
    	for (String sectionName: ini.keySet()) {
    		
    		if(counter<ini.size()){
    			counter++;
    			continue;
    		}
    			
    		if(sectionName.equalsIgnoreCase("Test"))
    			return rpm;
    		
    		System.out.println("["+sectionName+"]");
    		
    		Section section = ini.get(sectionName);
    		for (String optionKey: section.keySet()) {
    			System.out.println(section.get(optionKey));
    			rpm = rpm + Float.parseFloat(section.get(optionKey));
    		}
    	}
    	
    	return rpm;
    }
    
    public static void addNewVmToLoadBalancer(String dataCenterVmName, String loadGeneratorVmName) throws Exception{

      //Add to load generator      
      String url="http://"+ loadGeneratorVmName+"/test/horizontal/add?" +
      		"dns="+ URLEncoder.encode(dataCenterVmName,"UTF-8");

      System.out.println(url);
      
      String r = performGetRequest(url);
      
      while(r.contains("Invalid Data Center DNS"))
      {
    		  r = performGetRequest(url);
    		  sleep(1);
      }
      
      System.out.println("Added new VM: " + r);
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
    
    
    public static void setUpEc2ClientOject(){
    	
        //Configuring AWS credentials
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider("default").getCredentials();
            System.out.println("Credentials configured");
        }catch (Exception e) {
            throw new AmazonClientException("Credentials issue",e);
        }
        
        
        // Create the AmazonEC2Client object.
        ec2 = new AmazonEC2Client(credentials);
        Region usEast1 = Region.getRegion(Regions.US_EAST_1);
        ec2.setRegion(usEast1);
        
        //Create Security Group
        CreateSecurityGroupRequest csgr = new CreateSecurityGroupRequest();
        csgr.withGroupName("ProjectTwoSecurityGroup").withDescription("Allow on all ports");
        
        ec2.createSecurityGroup(csgr);
        
        IpPermission ipPermission = new IpPermission();
        
        ipPermission.withIpRanges("0.0.0.0/0")
        	            .withIpProtocol("tcp")
        	            .withFromPort(0)
        	            .withToPort(65535);

        AuthorizeSecurityGroupIngressRequest authorizeRequest = new AuthorizeSecurityGroupIngressRequest();
        authorizeRequest.withGroupName("ProjectTwoSecurityGroup").withIpPermissions(ipPermission);

        ec2.authorizeSecurityGroupIngress(authorizeRequest);
        System.out.println("Security Group Added");
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
        securityGroups.add("ProjectTwoSecurityGroup");
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
        

    public static void main(String[] args) throws Exception {

        setUpEc2ClientOject();
        
        //Prepare Load Generator
        String loadGenID = setUpSpotInstanceWithImage("ami-8ac4e9e0");
        addTagToInstanceID(loadGenID);
        String loadGeneratorVmName = getPublicDns(loadGenID);

        System.out.println("DNS of loadGenerator: " + loadGeneratorVmName);
        
        // Create Data Center
        String dataCenterID = setUpSpotInstanceWithImage("ami-349fbb5e");
        addTagToInstanceID(dataCenterID);
        String dataCenterVmName = getPublicDns(dataCenterID);
        
        System.out.println("DNS of Data Center is: " + dataCenterVmName);

        sleep(100);
                
        //Authenticate load generator
        String andrewId = "";
        String submissionPassword = "";
        
        String url = "http://"+loadGeneratorVmName+"/password?passwd=" + submissionPassword +
         		"&andrewid=" + andrewId;
 
        String result = performGetRequest(url);
        
        while(result.contains("Invalid Data Center DNS"))
        {
        	result = performGetRequest(url);
        	sleep(1);
        }
        
        
        //Start test on Data Center
        url="http://"+ loadGeneratorVmName+"/test/horizontal?"+
        		"dns="+URLEncoder.encode(dataCenterVmName,"UTF-8");

        String logid = performGetRequest(url);

        while(logid.contains("Invalid Data Center DNS"))
        {
        	logid = performGetRequest(url);
        	sleep(1);
        }
        
        System.out.println("\nLog Id is: " + logid);

        logid = parseLogId(logid);
        System.out.println("\nParsed Log Id is: " + logid);
        
        //Monitor Log
        final String logurl = "http://"+loadGeneratorVmName+"/log?name="+URLEncoder.encode(logid,"UTF-8");
        System.out.println("Log url is: " + logurl);
        
        float rpm = 0;
        float lastRpm = -1;
        while(rpm != -1)
        {
        	boolean success = downloadLogFile(logurl);
        	
        	if(success)
        		break;
        	
        	rpm = calculateRpm();
        	
        	if(rpm>=4000)
        		break;
        	
        	if(rpm>0 && rpm>lastRpm && rpm<4000)
        	{
                String dataCenter = setUpSpotInstanceWithImage("ami-349fbb5e");
                addTagToInstanceID(dataCenter);
                String dataCenterDns = getPublicDns(dataCenter);
                addNewVmToLoadBalancer(dataCenterDns, loadGeneratorVmName);
        	}
    		lastRpm = rpm;
        	sleep(80);
        }
    }
}
