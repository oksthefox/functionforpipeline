// this function is used to run proxy with ip address of windows machine

def IpForProxy(String ip)
{
    try
    {
        echo "open a terminal where it running proxy command in the backround..."
        sh """
            nohup ssh -o StrictHostKeyChecking=no user@${ip} "kubectl proxy --address=0.0.0.0 --port=8080 --accept-hosts=.*" &
        """
    }
    catch (Exception e)
    {
        echo "[ERROR]: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        error "Failed To Change Context: ${context}"
    }
}

// this function is used to change the context of kubectl
def changeContext(String context)
{
    try
    {
        echo 'Changing Context...'
        sh "kubectl config use-context ${context}"
    }
    catch (Exception e)
    {
        echo "[ERROR]: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        error "Failed To Change Context: ${context}"
    }
}
// this function is used to deploy the application to kubernetes it takes 4 parameters and search for the latest chart version and deploy it if it is not found it will fail the build
def deployToK8s(String packagename,String approotfolder,String environment,String deploymentName) 
{
    try
    {
        echo "Fetching the latest chart version..."
        def latestChart = sh(script: "ls ${approotfolder}/${packagename}*.tgz | sort -V | tail -n 1", returnStdout: true).trim()
        env.LATEST_CHART_PATH = latestChart

        echo "Deploying application using Helm..."
        def releaseName = "${deploymentName}"
        sh "helm upgrade --install ${releaseName} ${env.LATEST_CHART_PATH} --set global.env=${environment}"
    }
    catch (Exception e)
    {
        echo "[ERROR]: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        error "Failed To Change Context: ${context}"
    }
}
// this function is used to pull the tcp port of the service
def  pullTcp(String servicename)
{
    try
    {
        def tcpport=sh(script: "kubectl get service ${servicename} -o=jsonpath='{.spec.ports[*].nodePort}'", returnStdout: true).trim()
        env.KUBECTLTCPPORT=tcpport
        echo "TCP port is ${env.KUBECTLTCPPORT}"
    }
    catch (Exception e)
    {
        echo "[ERROR]: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        error "Failed To Change Context: ${context}"
    }
}
// this function is used to rollout the deployment
def rolloutK8S(String deploymentName1, String deploymentName2)
{
    try
    {
        echo 'Performing rollout restart...'
        sh "kubectl rollout restart deployment/${deploymentName1}"
        sh "kubectl rollout restart deployment/${deploymentName2}"
        sh "kubectl get all"
    }
    catch (Exception e)
    {
        echo "[ERROR]: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        error "Failed To Change Context: ${context}"
    }
}
// this function is used to test the application
def testingTestEnv(String ip, String kubectltcpport)
{
    try 
    {
        retry(30)
        {
            sh "sleep 10"
            sh "curl http://${ip}:${kubectltcpport}"
        }
    } 
    catch (Exception e) 
    {
        echo "Test Failed"
        currentBuild.result = 'FAILURE'
        error "Test Failed"
    }
}
// this function is used to close the proxy
def closingProxy(String ip)
{
    try
    {
        echo "closing proxy..."
        sh """
        ssh -o StrictHostKeyChecking=no user@${ip} "taskkill /F /IM kubectl.exe"
        """
    }
    catch (Exception e)
    {
        echo "[ERROR]: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        error "Failed To Change Context: ${context}"
    }
}


return this