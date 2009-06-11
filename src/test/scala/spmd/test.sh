#!/bin/sh

bin/spmd &
sleep 1

scala -cp target/classes:target/test-classes spmd.TestNode -name testnode1 &
PID1=$!
sleep 1
scala -cp target/classes:target/test-classes spmd.TestNode -name testnode2 &
PID2=$!
sleep 1

scala -cp target/classes:target/test-classes spmd.Test &
PID3=$!
sleep 2

kill -9 $PID1
kill -9 $PID2
kill -9 $PID3
