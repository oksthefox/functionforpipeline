def changeCheck (String jenkinsfile)
{
    def changeSets = currentBuild.changeSets
    if(changeSets.size()==0)
    {
        echo "no changes, ran manually proceeding"
        env.RELEVANT_CHANGES = "true"
    }
    else
    {
        def modifiedFiles = []
        for(changeSet in changeSets) 
        {
            for(item in changeSet) 
            {
                modifiedFiles += item.getAffectedPaths()
            }
        }
        modifiedFiles = modifiedFiles.minus("$jenkinsfile")
        if (modifiedFiles.isEmpty()) 
        {
            println('Skipping pipeline execution as the only change is to the Jenkinsfile.')
            env.RELEVANT_CHANGES = "false"
        }
        else
        {
            env.RELEVANT_CHANGES = "true"
        }
    }

}


// this function, sends a new version of the chart to GCS if there are changes in the mynewchart directory
def packageHelmChart(String folder, String bucket, String bucketFolder) {
    // Check for changes in the mynewchart directory
    def chartChanges = sh(script: "git diff --name-only HEAD~1 HEAD | grep mychart || true", returnStdout: true).trim()

    // Fetch latest chart from GCS
    def latestChart = sh(script: "gsutil ls gs://${bucket}/${bucketFolder}/myproject*.tgz | sort -V | tail -n 1", returnStdout: true).trim()

    sh "gsutil cp ${latestChart} ${folder}/"

    if (chartChanges) {
        // Unpack the chart
        sh "mkdir -p ${folder}/unpackedChart"
        sh "tar -xzvf ${folder}/myproject*.tgz -C ${folder}/unpackedChart"

        // Copy changes from static mynewchart to the unpacked version, excluding Chart.yaml
        sh "rsync -av --exclude='Chart.yaml' ${folder}/mychart/ ${folder}/unpackedChart/myproject/"

        // Determine the type of change
        def changeType = 'patch'  // default to patch

        if (chartChanges.contains("Chart.yaml")) {
            changeType = 'major'
        } else if (chartChanges.contains("templates/")) {
            changeType = 'minor'
        }

        // Bump the version
        sh "bash ${folder}/scripts/versionBump.sh ${changeType} ${folder}/unpackedChart/myproject/Chart.yaml"

        // Extract the new version from Chart.yaml using awk
        def newVersion = sh(script: "awk '/name: myproject/{getline; print \$2}' ${folder}/unpackedChart/myproject/Chart.yaml", returnStdout: true).trim()

        // Predict the packaged chart name
        def packagedChartName = "myproject-${newVersion}.tgz"

        // Repackage the chart
        sh "helm package ${folder}/unpackedChart/myproject -d ${folder}"

        // Upload the repackaged chart to GCS
        sh "gsutil cp ${folder}/${packagedChartName} gs://${bucket}/${bucketFolder}/"

        // Cleanup
        sh "rm -rf ${folder}/unpackedChart"
    }
}





def terraformApply(String folder, String clusterName, String zone, String project)
{
    // Change the current working directory to where your Terraform files are
    dir(${folder}) 
    {
        // Initialize Terraform
        sh 'terraform init'
        sh 'terraform apply -auto-approve'
        echo 'Updating kubectl context...'
        sh "gcloud container clusters get-credentials ${clusterName} --zone ${zone} --project ${project}"
    }
}


def dockerLogin()
{
    // Use the "withCredentials" block to securely access Docker credentials
    withCredentials([usernamePassword(credentialsId: 'DockerLogin', usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) 
    {
    // Replace 'your-docker-registry' with your Docker registry URL (e.g., Docker Hub)
        sh "docker login -u ${DOCKER_USERNAME} -p ${DOCKER_PASSWORD}"
    }
}
def IpForProxy(String ip)
{
    echo "open a terminal where it running proxy command in the backround..."
    sh """
        nohup ssh -o StrictHostKeyChecking=no user@${ip} "kubectl proxy --address=0.0.0.0 --port=8080 --accept-hosts=.*" &
    """
}
def CleanupWorkspace()
{
    echo 'Performing cleanup of workspace...'
    sh 'rm -rf *'
    
}
def CleanupDocker(String folder1, String folder2)
{
    echo 'Performing cleanup of docker images'
    sh "docker images | grep -w ${folder1} | grep -w [0-9]*\\.[0-9]* | awk '{print \$2}' | xargs -I {} docker rmi ${folder1}:{}"
    sh "docker images | grep -w ${folder2} | grep -w [0-9]\\.[0-9]* | awk '{print \$2}' | xargs -I {} docker rmi ${folder2}:{}"
}
def GitClone(String giturl)
{
    echo 'Cloning repository...'
    sh "git clone ${giturl}"
}
def InstallDependencies(String AnsibleFolder, String PlaybookFile)
{
    sh "ansible-playbook ${AnsibleFolder}/${PlaybookFile}"
}
def BuildDocker(String folder ,String image)
{
    try
    {
        def changedFiles = sh(script: "git diff --name-only HEAD~1 HEAD", returnStdout: true).trim()

        def hasRelevantChanges = changedFiles.any( it.startsWith("${folder}") )

        if (!hasRelevantChanges)
        {
            echo "No changes in ${folder}, skipping build"
            return
        }

        dir("${folder}")
        {
            echo "Building ${image}"
            sh "docker build -t ${image}:1.${BUILD_NUMBER} -t ${image}:latest ."
            sh "docker push --all-tags ${image}"
        }
    }
    catch (Exception e)
    {
        echo "[ERROR]: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        error "Failed To Build Docker Image: ${image}"
    }
}
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
def deployToK8s(String packagename,String approotfolder,String environment,String deploymentName) 
{
    
    echo "Fetching the latest chart version..."
    def latestChart = sh(script: "ls ${approotfolder}/${packagename}*.tgz | sort -V | tail -n 1", returnStdout: true).trim()
    env.LATEST_CHART_PATH = latestChart

    echo "Deploying application using Helm..."
    def releaseName = "${deploymentName}"
    sh "helm upgrade --install ${releaseName} ${env.LATEST_CHART_PATH} --set global.env=${environment}"

}
def  pullTcp(String servicename)
{
    def tcpport=sh(script: "kubectl get service ${servicename} -o=jsonpath='{.spec.ports[*].nodePort}'", returnStdout: true).trim()
    env.KUBECTLTCPPORT=tcpport
    echo "TCP port is ${env.KUBECTLTCPPORT}"
}
def rolloutK8S(String deploymentName1, String deploymentName2)
{
    echo 'Performing rollout restart...'
    sh "kubectl rollout restart deployment/${deploymentName1}"
    sh "kubectl rollout restart deployment/${deploymentName2}"
    sh "kubectl get all"
}
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
def closingHelm(String release)
{
    echo "closing test enviroment..."
    sh "helm uinstall ${release}"
}
def closingProxy(String ip)
{
    echo "closing proxy..."
    sh """
    ssh -o StrictHostKeyChecking=no user@${ip} "taskkill /F /IM kubectl.exe"
    """
}

return this