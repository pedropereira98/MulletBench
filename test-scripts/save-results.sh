RESULTS_FOLDER=${1}
IDENTIFIER=${2}
TEST_PATH=${3}
CONFIG_FILE=${4:-null}
ORCH_FOLDER=${5:-/tmp/mulletbench-orch}
PLOT_FOLDER=${6:-~/MulletBench/results-plot}

if [ $# -eq 1 ]; then
    >&2 echo "Missing arguments"
    exit 1
fi

echo "Clearing data folder" && rm -rf $PLOT_FOLDER/data && mkdir $PLOT_FOLDER/data
echo "Clearing images folder" && rm -rf $PLOT_FOLDER/images && mkdir $PLOT_FOLDER/images
echo "Copying test results" && cp $ORCH_FOLDER/results/$RESULTS_FOLDER/* $PLOT_FOLDER/data
echo "Generating plots for test $RESULTS_FOLDER" && cd $PLOT_FOLDER && python3 plot.py
mkdir -p $PLOT_FOLDER/results/$IDENTIFIER/$TEST_PATH
echo "Moving test data to $IDENTIFIER/$TEST_PATH" && mv $PLOT_FOLDER/data $PLOT_FOLDER/results/$IDENTIFIER/$TEST_PATH
echo "Moving test plots to $IDENTIFIER/$TEST_PATH" && mv $PLOT_FOLDER/images $PLOT_FOLDER/results/$IDENTIFIER/$TEST_PATH
echo "Getting test results" && docker logs --tail 1500 mulletbench-orchestrator | awk 'BEGIN { found = 0 } /Test results:/ { output = ""; found = 1 } found { output = output $0 ORS } END { printf "%s", output }' > $PLOT_FOLDER/results/$IDENTIFIER/$TEST_PATH/results.txt
echo "Removing original test results folder" | sudo -S rm -rf $ORCH_FOLDER/results/$RESULTS_FOLDER

if [ "$CONFIG_FILE" != "null" ]; then
    echo "Copying config file to $PLOT_FOLDER/results/$IDENTIFIER/$TEST_PATH"
    cp $CONFIG_FILE $PLOT_FOLDER/results/$IDENTIFIER/$TEST_PATH/../config.yaml
fi
