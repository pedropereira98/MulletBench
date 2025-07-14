#!/bin/sh

TEST_DIR="TestDir"


if [ ! -d "$TEST_DIR" ]; then
  printf "## Creating test directory\n"
  mkdir "$TEST_DIR"
fi

# ---------------------------------------------
printf "\n## Testing Write Throughput\n"

OUTPUT=$(fio --name=write_throughput --directory="$TEST_DIR" \
  --numjobs=8 --size=1G --time_based --runtime=60s --ramp_time=2s \
  --ioengine=libaio --direct=1 --verify=0 --bs=1M --rw=write --group_reporting=1)

# Extract line with bw=
STR=$(echo "$OUTPUT" | grep -m 1 -Po "bw=[^,]+")

WRITE_BPS=$(echo "$STR" | grep -Po "\d+\.?\d*[KMG]B/s")

printf "Write Throughput = %s\n" "$WRITE_BPS"

# ---------------------------------------------
printf "\n## Testing Write IOPS\n"

OUTPUT=$(fio --name=write_iops --directory="$TEST_DIR" \
  --numjobs=8 --size=512M --time_based --runtime=60s --ramp_time=2s \
  --ioengine=libaio --direct=1 --verify=0 --bs=512 --rw=randwrite --group_reporting=1)

STR=$(echo "$OUTPUT" | grep -m 1 -Po "IOPS=[^,]+")

WRITE_IOPS=$(echo "$STR" | grep -Po "\d+\.?\d*")
printf "Write IOPS = %s\n" "$WRITE_IOPS"

# ---------------------------------------------
printf "\n## Testing Read Throughput\n"

OUTPUT=$(fio --name=read_throughput --directory="$TEST_DIR" \
  --numjobs=8 --size=1G --time_based --runtime=60s --ramp_time=2s \
  --ioengine=libaio --direct=1 --verify=0 --bs=1M --rw=read --group_reporting=1)

STR=$(echo "$OUTPUT" | grep -m 1 -Po "bw=[^,]+")

READ_BPS=$(echo "$STR" | grep -Po "\d+\.?\d*[KMG]B/s")

printf "Read Throughput = %s %s\n" "$READ_BPS"

# ---------------------------------------------
printf "\n## Testing Read IOPS\n"

OUTPUT=$(fio --name=read_iops --directory="$TEST_DIR" \
  --numjobs=8 --size=512M --time_based --runtime=60s --ramp_time=2s \
  --ioengine=libaio --direct=1 --verify=0 --bs=1k --rw=randread --group_reporting=1)

STR=$(echo "$OUTPUT" | grep -m 1 -Po "IOPS=[^,]+")

READ_IOPS=$(echo "$STR" | grep -Po "\d+\.?\d*")
printf "Read IOPS = %s\n" "$READ_IOPS"

# ---------------------------------------------
printf "\n## Saving Results\n"

printf "write_bps: %s\nwrite_iops: %s\nread_bps: %s\nread_iops: %s\n" \
  "$WRITE_BPS" "$WRITE_IOPS" "$READ_BPS" "$READ_IOPS" > result.txt

printf "\n## Performing Cleanup\n"
rm -rf "$TEST_DIR"
