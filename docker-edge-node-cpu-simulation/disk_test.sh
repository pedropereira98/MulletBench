#/bin/sh

TEST_DIR="TestDir"

printf "## Creating test directory\n"

if ! [ -d TEST_DIR ] ;then
  mkdir $TEST_DIR
fi

printf "\n## Testing Write Throughput\n"

STR=$(fio --name=write_throughput --directory=$TEST_DIR --numjobs=8 --size=1G --time_based --runtime=60s --ramp_time=2s --ioengine=libaio --direct=1 --verify=0 --bs=1M --rw=write --group_reporting=1 | grep -m 1 -Po "\(\d+MB\/s\)")
WRITE_BPS=${STR:1:-4}
printf "Write Throughput = $WRITE_BPS\n"

printf "\n## Testing Write IOPS\n"

STR=$(fio --name=write_iops --directory=$TEST_DIR --numjobs=8 --size=512M --time_based --runtime=60s --ramp_time=2s --ioengine=libaio --direct=1 --verify=0 --bs=512 --rw=randwrite --group_reporting=1 | grep -m 1 -Po "IOPS=[^,]+")
WRITE_IOPS=${STR:5}

printf "Write IOPS = $WRITE_IOPS\n"

printf "\n## Testing Read Throughput\n"

STR=$(fio --name=read_throughput --directory=$TEST_DIR --numjobs=8 --size=1G --time_based --runtime=60s --ramp_time=2s --ioengine=libaio --direct=1 --verify=0 --bs=1M --rw=read --group_reporting=1 | grep -m 1 -Po "\(\d+MB\/s\)")
READ_BPS=${STR:1:-4}

printf "Read Throughput = $READ_BPS\n"

printf "\n## Testing Read IOPS\n"

STR=$(fio --name=read_iops --directory=$TEST_DIR --numjobs=8 --size=512M --time_based --runtime=60s --ramp_time=2s --ioengine=libaio --direct=1 --verify=0 --bs=1k --rw=randread --group_reporting=1 | grep -m 1 -Po "IOPS=[^,]+")
READ_IOPS=${STR:5}

printf "Read IOPS = $READ_IOPS\n"

printf "\n## Saving Results\n"

printf "write_bps: $WRITE_BPS\nwrite_iops: $WRITE_IOPS\nread_bps: $READ_BPS\nread_iops: $READ_IOPS" | cat > result.txt

printf "\n## Performing Cleanup\n"

rm -rf $TEST_DIR
