#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

#define MILLION  1000000
#define THOUSAND 1000
#define MAX_PATH_SIZE 500       // Max size for results paths
#define START_CPU_VALUE 1       // Value for 1st iteration's CPU setting
#define DELTA_DIVISOR 2         // Bigger value equals a smaller adjustment per iteration
#define MAX_ITERATIONS 10        // Max number of iterations

typedef struct sumiterations{
    long dhrystoneSum;
    long dhrystoneIterations;
    long dhrystoneOptimizedSum;
    long dhrystoneOptimizedIterations;
    double whetstoneSum;
    long whetstoneIterations;
    double whetstoneOptimizedSum;
    long whetstoneOptimizedIterations;
} SumIterations;

typedef struct averages{
    double dhrystoneAvg;
    double dhrystoneOptimizedAvg;
    double whetstoneAvg;
    double whetstoneOptimizedAvg;
} Averages;

typedef struct avgDelta{
    double dhrystoneDelta;
    double dhrystoneOptimizedDelta;
    double whetstoneDelta;
    double whetstoneOptimizedDelta;
} AverageDelta;

typedef enum Current_metric{
    DHRYSTONE,
    DHRYSTONEOPTIMIZED,
    WHETSTONE,
    WHETSTONEOPTIMIZED
} CurrentMetric;

//String literals
char *dhrystonePattern =       "This machine benchmarks at %ld dhrystones/second";
char *whetstonePattern =       "C Converted Double Precision Whetstones: %lf MIPS";
char *dhrystoneMark =          "Running dhrystone default";
char *dhrystoneOptimizedMark = "Running dhrystone with -O";
char *whetstoneMark =          "Running whetstone default";
char *whetstoneOptimizedMark = "Running whetstone with -O";
char *dockerRunPattern =       "docker run --cpus %.3lf --name test-machine edge-node-bench 2>&1 | tee %s";
char *resultFilePattern =      "./results/optimizer-run-%.3lfcpu.txt";
char *removeContainerCmd =    "docker rm test-machine";



int updateCurrentMetric(char *line, CurrentMetric *metric){
    int changed = 1;

    if(!strncmp(line, dhrystoneMark, strlen(dhrystoneMark) )){
        *metric = DHRYSTONE;
    } else if(!strncmp(line, dhrystoneOptimizedMark, strlen(dhrystoneOptimizedMark) )){
        *metric = DHRYSTONEOPTIMIZED;
    } else if(!strncmp(line, whetstoneMark, strlen(whetstoneMark) )){
        *metric = WHETSTONE;
    } else if(!strncmp(line, whetstoneOptimizedMark, strlen(whetstoneOptimizedMark) )){
        *metric = WHETSTONEOPTIMIZED;
    } else {
        changed = 0;
    }

    return changed;
}

//TODO: beautify by setting current pointer in avg struct and current pattern pointer?
void updateAvgs(SumIterations * avgs, char *line, CurrentMetric *metric){
    long dhrystoneMeasurement = 0;
    double whetstoneMeasurement = 0;
    
    if(!updateCurrentMetric(line, metric)){
        switch(*metric){
            case DHRYSTONE:
                if(sscanf(line, dhrystonePattern, &dhrystoneMeasurement)> 0){
                    avgs->dhrystoneSum += dhrystoneMeasurement;
                    avgs->dhrystoneIterations++;
                }
                break;
            case DHRYSTONEOPTIMIZED:
                if(sscanf(line, dhrystonePattern, &dhrystoneMeasurement) > 0){
                    avgs->dhrystoneOptimizedSum += dhrystoneMeasurement;
                    avgs->dhrystoneOptimizedIterations++;
                }
                break;
            case WHETSTONE:
                if(sscanf(line, whetstonePattern, &whetstoneMeasurement) > 0){
                    avgs->whetstoneSum += whetstoneMeasurement;
                    avgs->whetstoneIterations++;
                }
                break;
            case WHETSTONEOPTIMIZED:
                if(sscanf(line, whetstonePattern, &whetstoneMeasurement) > 0){
                    avgs->whetstoneOptimizedSum += whetstoneMeasurement;
                    avgs->whetstoneOptimizedIterations++;
                }
                break;
        }
    }
}

SumIterations * newSumIterations(){
       SumIterations * avgs = malloc(sizeof(SumIterations));

       memset(avgs, 0,sizeof(SumIterations));
       
       return avgs;
}

Averages * newAveragesWithValues(double dhrystone, double dhrystoneOptimized, double whetstone, double whetstoneOptimized){
    Averages * avgs = malloc(sizeof(Averages));

    avgs->dhrystoneAvg = dhrystone;
    avgs->dhrystoneOptimizedAvg = dhrystoneOptimized;
    avgs->whetstoneAvg = whetstone;
    avgs->whetstoneOptimizedAvg = whetstoneOptimized;
    
    return avgs;
}

AverageDelta * newAverageDelta(){
    AverageDelta * avgDelta = malloc(sizeof(AverageDelta));

    avgDelta->dhrystoneDelta = 0;
    avgDelta->dhrystoneOptimizedDelta = 0;
    avgDelta->whetstoneDelta = 0;
    avgDelta->whetstoneOptimizedDelta = 0;

    return avgDelta;
}

Averages *extractAveragesFromResultsFile(char *path){
    char *line = NULL;
    size_t lineSize = 0;
    FILE *file = fopen(path, "r");
    SumIterations * sumIterations = newSumIterations();
    Averages * avgs = NULL;
    CurrentMetric *metric = malloc(sizeof(CurrentMetric));
    *metric = DHRYSTONE;

    if(file==NULL){
        printf("Could not open file %s\n", path);
        exit(-1);
    } else{
        printf("Opened file: %s\n", path);
    }

    while(getline(&line, &lineSize, file) != -1){
        updateAvgs(sumIterations, line, metric);
    }
    free(line);
    fclose(file);

    avgs = newAveragesWithValues(((double) sumIterations->dhrystoneSum)/sumIterations->dhrystoneIterations, 
        ((double) sumIterations->dhrystoneOptimizedSum)/sumIterations->dhrystoneOptimizedIterations,
        ((double) sumIterations->whetstoneSum)/sumIterations->whetstoneIterations, 
        ((double) sumIterations->whetstoneOptimizedSum)/sumIterations->whetstoneOptimizedIterations);
    
    free(sumIterations);

    return avgs;
}

AverageDelta * calculate_avg_deltas(Averages * target, Averages * run){
    AverageDelta * avgDelta = newAverageDelta();

    avgDelta->dhrystoneDelta = (run->dhrystoneAvg / target->dhrystoneAvg) - 1;
    avgDelta->dhrystoneOptimizedDelta = (run->dhrystoneOptimizedAvg / target->dhrystoneOptimizedAvg) - 1;
    avgDelta->whetstoneDelta = (run->whetstoneAvg / target->whetstoneAvg) - 1;
    avgDelta->whetstoneOptimizedDelta = (run->whetstoneOptimizedAvg / target->whetstoneOptimizedAvg) - 1;

    return avgDelta;
}

void print_avgs(Averages * avgs){
    printf("Avg dhrystone: %lf M\n", avgs->dhrystoneAvg / MILLION);
    printf("Avg dhrystoneOptimized: %lf M\n", avgs->dhrystoneOptimizedAvg / MILLION);
    printf("Avg whetstone: %lf K\n", avgs->whetstoneAvg / THOUSAND);
    printf("Avg whetstoneOptimized: %lf K\n", avgs->whetstoneOptimizedAvg / THOUSAND);
}

void print_deltas(AverageDelta * deltas){
    printf("Delta dhrystone: %lf %%\n", deltas->dhrystoneDelta * 100);
    printf("Delta dhrystone: %lf %%\n", deltas->dhrystoneOptimizedDelta * 100);
    printf("Delta whetstone: %lf %%\n", deltas->whetstoneDelta * 100);
    printf("Delta whetstone optimized: %lf %%\n", deltas->whetstoneOptimizedDelta * 100);
}

//TODO: Apply multiplicative increase multiplicative decrease?
double calculate_next_cpu_trial(double current_cpu, AverageDelta * deltas){
    static double previousAvgDelta = 0.0;
    static int divisor = DELTA_DIVISOR;
    double avgDelta = (deltas->dhrystoneDelta + deltas->dhrystoneOptimizedDelta + deltas->whetstoneDelta + deltas->whetstoneOptimizedDelta) / 4;

    //If delta sign changes, increase divisor to make smaller changes
    if(previousAvgDelta != 0 && (previousAvgDelta * avgDelta) < 0){
        divisor *= 2;
    }
    previousAvgDelta = avgDelta;
    double next_cpu = current_cpu - ((avgDelta * current_cpu) / divisor);
    if(next_cpu < (current_cpu / 2)){
        next_cpu = current_cpu / 2;
    }
    printf("Avg delta: %lf ; Next CPU value: %lf \n\n\n", avgDelta, next_cpu);

    return next_cpu;
}

//TODO: register whetstone and dhrystone cycles of the target machine, use same number of cycles while optimizing
int main(int argc, char * argv[]){
    char *cmd = malloc(sizeof(dockerRunPattern) + MAX_PATH_SIZE);
    char *results_path = malloc(MAX_PATH_SIZE);
    char *path = argv[1];
    Averages * expected = extractAveragesFromResultsFile(path);
    double next_cpu = START_CPU_VALUE;
    bool stop = false;
    AverageDelta * deltas = NULL;
    Averages *avgs = newAveragesWithValues(0,0,0,0);
    int iterations = 0;

    if(argc == 2){
        printf("Optimizing CPU docker setting.\n");
    } else {
        printf("Incorrect number of arguments, expected 2 arguments, but received %d. Usage:\n"
        "./optimize-docker-cpu reference-results-file-path\n", argc);
        exit(0);
    }

    
    printf("Target values:\n");
    print_avgs(expected);
    printf("Starting first test\n");

    while(!stop && iterations < MAX_ITERATIONS){
        system(removeContainerCmd);
        sprintf(results_path, resultFilePattern, next_cpu);
        sprintf(cmd, dockerRunPattern, next_cpu, results_path);
        system(cmd);
        avgs = extractAveragesFromResultsFile(results_path); 
        print_avgs(avgs);
        deltas = calculate_avg_deltas(expected, avgs);
        print_deltas(deltas);
        next_cpu = calculate_next_cpu_trial(next_cpu, deltas);
        iterations++;
    }
    free(avgs);

    return 0;
}