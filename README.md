jetty is a (very) simple Just-In-Time compiler for [tenyr](http://tenyr.info).
Currently, it generates a new class with a static function for each detected
basic block, after a basic block has been executed a certain number of times.

Execution speed on long-running processes is about twice as high as for the C
simulator (tsim), although this comparison is not quite fair since jetty does
not yet support devices (only plain memory accesses).

Put soot-trunk.jar in the repository root directory to compile.

Run JettySim with one command-line argument, a `.texe` to simulate.

