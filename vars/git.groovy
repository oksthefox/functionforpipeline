def GitClone(String giturl)
{
    try
    {
        echo 'Cloning repository...'
        sh "git clone ${giturl}"
    }
    catch (Exception e)
    {
        echo "[ERROR]: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        error "Failed To Clone Repository: ${giturl}"
    }
}


return this