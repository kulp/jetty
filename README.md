jetty is a (very) simple Just-In-Time compiler for [tenyr](http://tenyr.info).
Currently, it generates a new class with a static function for each detected
basic block, after a basic block has been executed a certain number of times.

Execution on long-running processes is more than twice as fast as for the C
simulator (tsim), and will get much better when the JIT is optimised.

Put soot-trunk.jar in the repository root directory to compile.

Run JettySim with one command-line argument, a `.texe` to simulate.

