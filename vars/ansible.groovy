def InstallDependencies(String AnsibleFolder, String PlaybookFile)
{
    try
    {
        sh "ansible-playbook ${AnsibleFolder}/${PlaybookFile}"
    }
    catch (Exception e)
    {
        echo "[ERROR]: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        error "Failed To Change Context: ${context}"
    }
}




return this

