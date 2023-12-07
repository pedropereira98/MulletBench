RESULTS_FOLDER=${1}
TEST_PATH=${2}
ORCH_FOLDER=${3:-/tmp/mulletbench-orch}
PLOT_FOLDER=${4:-~/MulletBench/results-plot}

if [ $# -eq 1 ]; then
    >&2 echo "Missing arguments"
    exit 1
fi

echo "Clearing data folder" && rm -rf $PLOT_FOLDER/data && mkdir $PLOT_FOLDER/data
echo "Clearing images folder" && rm -rf $PLOT_FOLDER/images && mkdir $PLOT_FOLDER/images
echo "Copying test results" && cp $ORCH_FOLDER/results/$RESULTS_FOLDER/* $PLOT_FOLDER/data
echo "Generating plots for test $RESULTS_FOLDER" && cd $PLOT_FOLDER && python3 plot.py
mkdir $PLOT_FOLDER/$TEST_PATH
echo "Moving test data to $TEST_PATH" && mv $PLOT_FOLDER/data $PLOT_FOLDER/$TEST_PATH
echo "Moving test plots to $TEST_PATH" && mv $PLOT_FOLDER/images $PLOT_FOLDER/$TEST_PATH
echo "Getting test results" && docker logs --tail 1500 mulletbench-orchestrator | awk 'BEGIN { found = 0 } /Test results:/ { output = ""; found = 1 } found { output = output $0 ORS } END { printf "%s", output }' > $PLOT_FOLDER/$TEST_PATH/results.txt
echo "Removing original test results folder" && sudo rm -rf $ORCH_FOLDER/results/$RESULTS_FOLDER