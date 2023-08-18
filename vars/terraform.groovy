def terraformApply(String folder, String clusterName, String zone, String project)
{
    try{
            // Check for changes in the mynewchart directory
            dir(${folder}) 
            {
                // Initialize Terraform
                sh 'terraform init'
                sh 'terraform apply -auto-approve'
                echo 'Updating kubectl context...'
                sh "gcloud container clusters get-credentials ${clusterName} --zone ${zone} --project ${project}"
            }
    }
    catch (Exception e)
    {
        echo "[ERROR]: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        error "Failed To Change Context: ${context}"
    }
}


return this