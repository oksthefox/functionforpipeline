// this function, sends a new version of the chart to GCS if there are changes in the mynewchart directory
def packageHelmChart(String folder, String bucket, String bucketFolder) 
{
    try
    {
        // Check for changes in the mynewchart directory
        def chartChanges = sh(script: "git diff --name-only HEAD~1 HEAD | grep mychart || true", returnStdout: true).trim()

        // Fetch latest chart from GCS
        def latestChart = sh(script: "gsutil ls gs://${bucket}/${bucketFolder}/myproject*.tgz | sort -V | tail -n 1", returnStdout: true).trim()

        sh "gsutil cp ${latestChart} ${folder}/"

        if (chartChanges) 
        {
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
    catch (Exception e)
    {
        echo "[ERROR]: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        error "Failed To Package Helm Chart"
    }
}
def closingHelm(String release)
{
    try{
    echo "closing test enviroment..."
    sh "helm uinstall ${release}"
    }
    catch (Exception e)
    {
        echo "[ERROR]: ${e.getMessage()}"
        currentBuild.result = 'FAILURE'
        error "Failed To Close Helm"
    }
}


return this