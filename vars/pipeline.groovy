
//this function is used to check if there are any changes in the repo other than the Jenkinsfile

def changeCheck (String jenkinsfile)
{
    try
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
}

def CleanupWorkspace()
{
    try
    {
        echo 'Performing cleanup of workspace...'
        sh 'rm -rf *'
    }
    catch (Exception e)
    {
        echo "[ERROR]: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        error "Failed To Cleanup Workspace"
    }   
}


return this