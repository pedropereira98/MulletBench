#!/bin/bash

ANSIBLE_PATH=~/MulletBench/ansible
NUMBER_RUNS=3
STARTING_RUN=1
SKIP_SHUTDOWN=false
DEFAULT_HOSTS_FILE=hosts.yml
while getopts "hn:s:t:a:crp:" flag; do
    case $flag in
        h)
            echo "./run-test.sh [-h] [-n x] [-s x] [-a x] [-r] [-c] -t x"
            echo "Options: "
            echo "  -h       Print help"
            echo "  -n       Specify number of runs (default = 3)"
            echo "  -s       Specify starting run number (default = 1)"
            echo "  -a       Specify path to ansible script directory"
            echo "  -r       Skip cleaning before and after each run"
            echo "  -c       Only clean"
            echo "  -t       Path to test to run (mandatory)"
            exit 1
            ;;
        n)
            echo "Changing number of runs to $OPTARG"
            NUMBER_RUNS=$OPTARG
            ;;
        s) 
            echo "Starting runs at $OPTARG"
            STARTING_RUN=$OPTARG
            ;;
        a) 
            echo "Using ansible path at $OPTARG"
            ANSIBLE_PATH=$OPTARG
            ;;
        r)  
            echo "Skipping cleaning"
            SKIP_SHUTDOWN=true
            ;;
        c) echo "Only cleaning"
            SKIP_RUN=true
            ;;
        t)
            echo "Running test $OPTARG"
            TEST_PATH=$OPTARG
            ;;
        \?)
            exit 
            ;;
    esac
done

shift "$(( OPTIND - 1 ))"

if [ -z "$TEST_PATH" ]; then
    echo 'Missing -t' >&2
    exit 1
fi


LAST_RUN=$(expr $NUMBER_RUNS + $STARTING_RUN - 1)

echo "$(date +%T) - Doing $NUMBER_RUNS runs of $TEST_PATH (starting at $STARTING_RUN)"

if [ "${TEST_PATH#*.}" = "yml" ] || [ "${TEST_PATH#*.}" = "yaml" ]; then #if given test path includes file
    FULL_HOSTS_PATH=~/MulletBench/results-plot/results/$TEST_PATH
    TEST_PATH=$(dirname $TEST_PATH)
else
    FULL_HOSTS_PATH=~/MulletBench/results-plot/results/$TEST_PATH/$DEFAULT_HOSTS_FILE
fi

if ! test -f "$FULL_HOSTS_PATH"; then
    echo "Test execution failed - $FULL_HOSTS_PATH does not exists"
    exit 1
fi

current_run=$STARTING_RUN
while [ $current_run -le $LAST_RUN ]
do
    if [ "$SKIP_SHUTDOWN" = false ]; then
        echo "$(date +%T) - Resetting config" && ansible-playbook $ANSIBLE_PATH/shutdown-playbook.yaml -i $FULL_HOSTS_PATH -t hard-reset
    else
        echo "$(date +%T) - Skipping reset"
    fi

    if [ "$SKIP_RUN" = true ]; then
        echo "$(date +%T) - Skipping runs" && exit 0
    fi

    echo "$(date +%T) - Starting test" && ansible-playbook $ANSIBLE_PATH/playbook.yaml -i $FULL_HOSTS_PATH

    # Check if orchestrator is running
    if [ "$( docker container inspect -f '{{.State.Status}}' mulletbench-orchestrator )" == "running" ]
    then
        echo "$(date +%T) - Orchestrator is running"
    else
        echo "$(date +%T) - Test execution failed, please retry" && exit 1
    fi

    sleep 5
    # Check if test has started successfully

    test_started_max_retries=20
    retries=0
    while [ "$(docker logs --tail 20 mulletbench-orchestrator | awk '/All clients started/ {print $0}' | wc -l)" -le 0 ]
    do
        if [ $retries -le $test_started_max_retries ]
        then
            retries=$((retries+1))
            sleep 10
        else
            echo "$(date +%T) - Test execution failed, please retry" && exit 2
            exit 1
        fi
    done

    echo "$(date +%T) - Test is running"

    TEST_ID=$(docker logs --tail 500 mulletbench-orchestrator | awk '/Results folder: [/]/ {a=$10} END{print a}' | cut -d'/' -f5)

    # check if orchestrator has finished
    orch_finished=0

    while [ $orch_finished == 0 ]
    do
        sleep 10
        if [ "$( docker container inspect -f '{{.State.Status}}' mulletbench-orchestrator )" == "exited" ]
        then 
            orch_finished=1
        fi
    done

    echo "$(date +%T) - Orchestrator is finished"

    echo "$(date +%T) - Saving test results" && ./save-results.sh $TEST_ID results/$TEST_PATH/run-$current_run

    if [ "$SKIP_SHUTDOWN" = false ]; then
        echo "$(date +%T) - Resetting config" && ansible-playbook $ANSIBLE_PATH/shutdown-playbook.yaml -i $FULL_HOSTS_PATH -t hard-reset
    else
        echo "$(date +%T) - Skipping reset"
    fi
    current_run=$((current_run+1))
done
