def dockerLogin()
{
    try
    {
        // Use the "withCredentials" block to securely access Docker credentials
        withCredentials([usernamePassword(credentialsId: 'DockerLogin', usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) 
        {
        // Replace 'your-docker-registry' with your Docker registry URL (e.g., Docker Hub)
            sh "docker login -u ${DOCKER_USERNAME} -p ${DOCKER_PASSWORD}"
        }
    }
    catch (Exception e)
    {
        echo "[ERROR]: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        error "Failed To Login To Docker"
    }
}
def CleanupDocker(String folder1, String folder2)
{
    try{
        echo 'Performing cleanup of docker images'
        sh "docker images | grep -w ${folder1} | grep -w [0-9]*\\.[0-9]* | awk '{print \$2}' | xargs -I {} docker rmi ${folder1}:{}"
        sh "docker images | grep -w ${folder2} | grep -w [0-9]\\.[0-9]* | awk '{print \$2}' | xargs -I {} docker rmi ${folder2}:{}"
    }
    catch (Exception e)
    {
        echo "[ERROR]: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        error "Failed To Cleanup Docker"
    }
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


def dockerLogin()
{
    try
    {
        // Use the "withCredentials" block to securely access Docker credentials
        withCredentials([usernamePassword(credentialsId: 'DockerLogin', usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) 
        {
        // Replace 'your-docker-registry' with your Docker registry URL (e.g., Docker Hub)
            sh "docker login -u ${DOCKER_USERNAME} -p ${DOCKER_PASSWORD}"
        }
    }
    catch (Exception e)
    {
        echo "[ERROR]: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        error "Failed To Login To Docker"
    }
}



return this