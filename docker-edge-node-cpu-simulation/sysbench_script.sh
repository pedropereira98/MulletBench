echo "$(date +%T) - cpu-max-prime=10k" && sysbench cpu --cpu-max-prime=10000 --time=120 run && sleep 30
echo "$(date +%T) - cpu-max-prime=20k" && sysbench cpu --cpu-max-prime=20000 --time=120 run && sleep 30
echo "$(date +%T) - cpu-max-prime=10k threads=2" && sysbench cpu --cpu-max-prime=10000 --time=120 --threads=2 run && sleep 30
echo "$(date +%T) - cpu-max-prime=20k threads=2" && sysbench cpu --cpu-max-prime=20000 --time=120 --threads=2 run && sleep 30
echo "$(date +%T) - cpu-max-prime=10k threads=4" && sysbench cpu --cpu-max-prime=10000 --time=120 --threads=4 run && sleep 30
echo "$(date +%T) - cpu-max-prime=20k threads=4" && sysbench cpu --cpu-max-prime=20000 --time=120 --threads=4 run && sleep 30
echo "$(date +%T) - cpu-max-prime=10k threads=8" && sysbench cpu --cpu-max-prime=10000 --time=120 --threads=8 run && sleep 30
echo "$(date +%T) - cpu-max-prime=20k threads=8" && sysbench cpu --cpu-max-prime=20000 --time=120 --threads=8 run && sleep 30
