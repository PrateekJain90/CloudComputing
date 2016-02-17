import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.ComputeManagementService;
import com.microsoft.azure.management.compute.models.*;
import com.microsoft.azure.management.network.NetworkResourceProviderClient;
import com.microsoft.azure.management.network.NetworkResourceProviderService;
import com.microsoft.azure.management.network.models.AzureAsyncOperationResponse;
import com.microsoft.azure.management.network.models.PublicIpAddressGetResponse;
import com.microsoft.azure.management.resources.ResourceManagementClient;
import com.microsoft.azure.management.resources.ResourceManagementService;
import com.microsoft.azure.management.storage.StorageManagementClient;
import com.microsoft.azure.management.network.models.DhcpOptions;
import com.microsoft.azure.management.storage.StorageManagementService;
import com.microsoft.azure.management.network.models.VirtualNetwork;
import com.microsoft.azure.utility.*;
import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.management.configuration.ManagementConfiguration;

import java.io.BufferedReader;
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
    private static ResourceManagementClient resourceManagementClient;
    private static StorageManagementClient storageManagementClient;
    private static ComputeManagementClient computeManagementClient;
    private static NetworkResourceProviderClient networkResourceProviderClient;

    // the source URI of VHD  
    private static String dataCenterVhdUri = "";
    private static String loadGeneratorVhdUri = "";

   
    // configuration for your application token
    private static String baseURI = "https://management.azure.com/";
    private static String basicURI = "https://management.core.windows.net/";
    private static String endpointURL = "https://login.windows.net/";

    private static String subscriptionId = "";
    private static String tenantID = "";
    private static String applicationID = "";
    private static String applicationKey = "";
    
    // configuration for your resource account/storage account
    private static String storageAccountName = "";
    private static String resourceGroupNameWithVhd = "";

    private static String region = "EastUs";
    
    private static String dataCenterVmName = "datacenter-vm";
    private static String dataCenterSize = VirtualMachineSizeTypes.STANDARD_A1;
    private static String dataCenterResourceGroupName = "datacentervm";
    
    private static String loadGeneratorVmName = "loadgenerator-vm";    
    private static String loadGeneratorSize = "STANDARD_D1";
    private static String loadGeneratorResourceGroupName = "loadgeneratorvm";

    // configuration for your virtual machine
    private static String adminName = "ubuntu";
    /**
      * Password requirements:
      * 1) Contains an uppercase character
      * 2) Contains a lowercase character
      * 3) Contains a numeric digit
      * 4) Contains a special character.
      */
    private static String adminPassword = "Cloud@projecttwo";
    
    private static Timer timer, timer2;

    public ProjectTwo() throws Exception{
        Configuration config = createConfiguration();
        resourceManagementClient = ResourceManagementService.create(config);
        storageManagementClient = StorageManagementService.create(config);
        computeManagementClient = ComputeManagementService.create(config);
        networkResourceProviderClient = NetworkResourceProviderService.create(config);
    }

    public static Configuration createConfiguration() throws Exception {
        // get token for authentication
        String token = AuthHelper.getAccessTokenFromServicePrincipalCredentials(
                        basicURI,
                        endpointURL,
                        tenantID,
                        applicationID,
                        applicationKey).getAccessToken();
      
        // generate Azure sdk configuration manager
        return ManagementConfiguration.configure(
                null, // profile
                new URI(baseURI), // baseURI
                subscriptionId, // subscriptionId
                token// token
                );
    }

    /***
     * Create a virtual machine given configurations.
     *
     * @param resourceGroupName: a new name for your virtual machine [customized], will create a new one if not already exist
     * @param vmName: a PUBLIC UNIQUE name for virtual machine
     * @param resourceGroupNameWithVhd: the resource group where the storage account for VHD is copied
     * @param sourceVhdUri: the Uri for VHD you copied
     * @param instanceSize
     * @param subscriptionId: your Azure account subscription Id
     * @param storageAccountName: the storage account where you VHD exist
     * @return created virtual machine IP
     */
    public static ResourceContext createVM (
        String resourceGroupName,
        String vmName,
        String resourceGroupNameWithVhd,
        String sourceVhdUri,
        String instanceSize,
        String subscriptionId,
        String storageAccountName) throws Exception {

        ResourceContext contextVhd = new ResourceContext(
                region, resourceGroupNameWithVhd, subscriptionId, false);
        ResourceContext context = new ResourceContext(
                region, resourceGroupName, subscriptionId, false);

        ComputeHelper.createOrUpdateResourceGroup(resourceManagementClient,context);
        context.setStorageAccountName(storageAccountName);
        contextVhd.setStorageAccountName(storageAccountName);
        context.setStorageAccount(StorageHelper.getStorageAccount(storageManagementClient,contextVhd));

        if (context.getNetworkInterface() == null) {
            if (context.getPublicIpAddress() == null) {
                NetworkHelper
                    .createPublicIpAddress(networkResourceProviderClient, context);
            }
            if (context.getVirtualNetwork() == null) {
                NetworkHelper
                    .createVirtualNetwork(networkResourceProviderClient, context);
            }

            VirtualNetwork vnet =  context.getVirtualNetwork();

            // set DhcpOptions
            DhcpOptions dop = new DhcpOptions();
            ArrayList<String> dnsServers = new ArrayList<String>(2);
            dnsServers.add("8.8.8.8");
            dop.setDnsServers(dnsServers);
            vnet.setDhcpOptions(dop);

            try {
                AzureAsyncOperationResponse response = networkResourceProviderClient.getVirtualNetworksOperations()
                    .createOrUpdate(context.getResourceGroupName(), context.getVirtualNetworkName(), vnet);
            } catch (ExecutionException ee) {
                if (ee.getMessage().contains("RetryableError")) {
                    AzureAsyncOperationResponse response2 = networkResourceProviderClient.getVirtualNetworksOperations()
                        .createOrUpdate(context.getResourceGroupName(), context.getVirtualNetworkName(), vnet);
                } else {
                    throw ee;
                }
            }


            NetworkHelper
                .createNIC(networkResourceProviderClient, context, context.getVirtualNetwork().getSubnets().get(0));

            NetworkHelper
                .updatePublicIpAddressDomainName(networkResourceProviderClient, resourceGroupName, context.getPublicIpName(), vmName);
        }

        System.out.println("[15319/15619] "+context.getPublicIpName());
        System.out.println("[15319/15619] Start Create VM...");

        try {
            // name for your VirtualHardDisk
            String osVhdUri = ComputeHelper.getVhdContainerUrl(context) + String.format("/os%s.vhd", vmName);

            VirtualMachine vm = new VirtualMachine(context.getLocation());

            vm.setName(vmName);
            vm.setType("Microsoft.Compute/virtualMachines");
            vm.setHardwareProfile(createHardwareProfile(context, instanceSize));
            vm.setStorageProfile(createStorageProfile(osVhdUri, sourceVhdUri));
            vm.setNetworkProfile(createNetworkProfile(context));
            vm.setOSProfile(createOSProfile(adminName, adminPassword, vmName));

            context.setVMInput(vm);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        // Remove the resource group will remove all assets (VM/VirtualNetwork/Storage Account etc.)
        // Comment the following line to keep the VM.
        // resourceManagementClient.getResourceGroupsOperations().beginDeleting(context.getResourceGroupName());
        // computeManagementClient.getVirtualMachinesOperations().beginDeleting(resourceGroupName,"project2.2");
        return context;
        }

    /***
     * Check public IP address of virtual machine
     *
     * @param context
     * @param vmName
     * @return public IP
     */
    public static String checkVM(ResourceContext context, String vmName, String resourceGroupName) {
        String ipAddress = null;

        try {
            VirtualMachine vmHelper = ComputeHelper.createVM(
                    resourceManagementClient, computeManagementClient, networkResourceProviderClient, storageManagementClient,
                    context, vmName, "ubuntu", "Cloud@projecttwo").getVirtualMachine();

            System.out.println("[15319/15619] "+vmHelper.getName() + " Is Created :)");
            while(ipAddress == null) {
                PublicIpAddressGetResponse result = networkResourceProviderClient.getPublicIpAddressesOperations().get(resourceGroupName, context.getPublicIpName());
                ipAddress = result.getPublicIpAddress().getIpAddress();
                Thread.sleep(10);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return ipAddress;
    }

    /***
     * Create a HardwareProfile for virtual machine
     *
     * @param context
     * @param instanceSize
     * @return created HardwareProfile
     */
    public static HardwareProfile createHardwareProfile(ResourceContext context, String instanceSize) {
        HardwareProfile hardwareProfile = new HardwareProfile();
        if (context.getVirtualMachineSizeType()!=null && !context.getVirtualMachineSizeType().isEmpty()) {
            hardwareProfile.setVirtualMachineSize(context.getVirtualMachineSizeType());
        } else {
            hardwareProfile.setVirtualMachineSize(instanceSize);
        }
        return hardwareProfile;
    }

    /***
     * Create a StorageProfile for virtual machine
     *
     * @param osVhdUri
     * @param sourceVhdUri
     * @return created StorageProfile
     */
    public static StorageProfile createStorageProfile(String osVhdUri, String sourceVhdUri) {
        StorageProfile storageProfile = new StorageProfile();

        VirtualHardDisk vHardDisk = new VirtualHardDisk();
        vHardDisk.setUri(osVhdUri);
        //set source image
        VirtualHardDisk sourceDisk = new VirtualHardDisk();
        sourceDisk.setUri(sourceVhdUri);

        OSDisk osDisk = new OSDisk("osdisk", vHardDisk, DiskCreateOptionTypes.FROMIMAGE);
        osDisk.setSourceImage(sourceDisk);
        osDisk.setOperatingSystemType(OperatingSystemTypes.LINUX);
        osDisk.setCaching(CachingTypes.NONE);

        storageProfile.setOSDisk(osDisk);

        return storageProfile;
    }

    /***
     * Create a NetworkProfile for virtual machine
     *
     * @param context
     * @return created NetworkProfile
     */
    public static NetworkProfile createNetworkProfile(ResourceContext context) {
        NetworkProfile networkProfile = new NetworkProfile();
        NetworkInterfaceReference nir = new NetworkInterfaceReference();
        nir.setReferenceUri(context.getNetworkInterface().getId());
        ArrayList<NetworkInterfaceReference> nirs = new ArrayList<NetworkInterfaceReference>(1);
        nirs.add(nir);
        networkProfile.setNetworkInterfaces(nirs);

        return networkProfile;
    }

    /***
     * Create a OSProfile for virtual machine
     *
     * @param adminName
     * @param adminPassword
     * @param vmName
     * @return created OSProfile
     */
    public static OSProfile createOSProfile(String adminName, String adminPassword, String vmName) {
        OSProfile osProfile = new OSProfile();
        osProfile.setAdminPassword(adminPassword);
        osProfile.setAdminUsername(adminName);
        osProfile.setComputerName(vmName);

        return osProfile;
    }
    
    public static void printInitialInfo(String vhdURL, String vmName){

    	System.out.println("Initializing Azure virtual machine:");
        System.out.println("Source VHD URL: "+vhdURL);
        System.out.println("Storage account: "+storageAccountName);
        System.out.println("Subscription ID: "+subscriptionId);
        System.out.println("Tenent ID: "+tenantID);
        System.out.println("Application ID: "+applicationID);
        System.out.println("Application Key: "+applicationKey);
        System.out.println("VM Name: "+ vmName);
    	
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
    
    public static void addNewVmToLoadBalancer() throws Exception{

      new ProjectTwo();
          	
      String seed = String.format("%d%d", (int) System.currentTimeMillis()%1000, (int)(Math.random()*1000));
      String addedDataCenterVmName = String.format("cloud%s%s", seed, "datacenter-vm");
      String addedDataCenterResourceGroupName = String.format("cloud%s%s", seed, "datacentervm");
      
      
      ResourceContext context = createVM (
									addedDataCenterResourceGroupName,
									addedDataCenterVmName,
									resourceGroupNameWithVhd,
									dataCenterVhdUri,
									dataCenterSize,
									subscriptionId,
									storageAccountName);
	    	                
      System.out.println(checkVM(context, addedDataCenterVmName, 
	    	                		addedDataCenterResourceGroupName));

      
      //Add to load generator      
      String url="http://"+ loadGeneratorVmName+".eastus.cloudapp.azure.com/test/horizontal/add?" +
      		"dns="+ URLEncoder.encode(addedDataCenterVmName+".eastus.cloudapp.azure.com", "UTF-8");

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
    

    /**
     * The main entry for the demo
     *
     * args0: resource group
     * args1: storage account
     * args2: image name data center
     * args3: image name load generator     
     * args4: subscription ID
     * args5: tenant ID
     * args6: application ID
     * args7: application Key
     */
    public static void main(String[] args) throws Exception {
        String seed = String.format("%d%d", (int) System.currentTimeMillis()%1000, (int)(Math.random()*1000));
        
        dataCenterResourceGroupName = String.format("cloud%s%s", seed, dataCenterResourceGroupName);
        dataCenterVmName = String.format("cloud%s%s", seed, dataCenterVmName);
        
        loadGeneratorResourceGroupName = String.format("cloud%s%s", seed, loadGeneratorResourceGroupName);
        loadGeneratorVmName = String.format("cloud%s%s", seed, loadGeneratorVmName);

        resourceGroupNameWithVhd = args[0].trim();
        storageAccountName = args[1].trim();
        dataCenterVhdUri = String.format("https://%s.blob.core.windows.net/system/Microsoft.Compute/Images/vhds/%s", storageAccountName, args[2].trim());
        loadGeneratorVhdUri = String.format("https://%s.blob.core.windows.net/system/Microsoft.Compute/Images/vhds/%s", storageAccountName, args[3].trim());        
        subscriptionId = args[4].trim();
        tenantID = args[5].trim();
        applicationID = args[6].trim();
        applicationKey = args[7].trim();
        
        // Create Load Generator
        
        printInitialInfo(loadGeneratorVhdUri, loadGeneratorVmName);

        ProjectTwo loadGeneratorVm = new ProjectTwo();

        System.out.println("Load Generator Configured");

        ResourceContext loadGeneratorContext = createVM (
                loadGeneratorResourceGroupName,
                loadGeneratorVmName,
                resourceGroupNameWithVhd,
                loadGeneratorVhdUri,
                loadGeneratorSize,
                subscriptionId,
                storageAccountName);

        System.out.println(checkVM(loadGeneratorContext, loadGeneratorVmName, loadGeneratorResourceGroupName));
        

        // Create Data Center
        
        printInitialInfo(dataCenterVhdUri, dataCenterVmName);

        ProjectTwo dataCenterVm = new ProjectTwo();

        System.out.println("Data Center Configured");
        
        ResourceContext dataCenterContext = createVM (
                dataCenterResourceGroupName,
                dataCenterVmName,
                resourceGroupNameWithVhd,
                dataCenterVhdUri,
                dataCenterSize,
                subscriptionId,
                storageAccountName);

        
        System.out.println(checkVM(dataCenterContext, dataCenterVmName, dataCenterResourceGroupName));

        sleep(100);
                
        //Authenticate load generator
        String andrewid = "";
        String submissionPassword = "";
        
        String url = "http://"+loadGeneratorVmName+".eastus.cloudapp.azure.com/password?passwd="+submissionPassword+
         		"&andrewid=" + andrewid;
 
        performGetRequest(url);
        
        //Start test on Data Center
        url="http://"+ loadGeneratorVmName+".eastus.cloudapp.azure.com/test/horizontal?" +
        		"dns="+ URLEncoder.encode(dataCenterVmName+".eastus.cloudapp.azure.com", "UTF-8");

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
        final String logurl = "http://"+loadGeneratorVmName+".eastus.cloudapp.azure.com/" +
				"log?name=" + URLEncoder.encode(logid,"UTF-8");
        
        float rpm = 0;
        float lastRpm = -1;
        while(rpm != -1)
        {
        	boolean result = downloadLogFile(logurl);
        	
        	if(result)
        		break;
        	
        	rpm = calculateRpm();
        	
        	if(rpm>=3000)
        		break;
        	
        	if(rpm>0 && rpm>lastRpm && rpm<3000)
        	{
        		addNewVmToLoadBalancer();
        	}
    		lastRpm = rpm;
        	sleep(100);
        }
    }
}
