SOOTJARS = soot-trunk.jar
PACKAGE  = ch/kulp/jetty
RUNFILE  = fib.texe

.PHONY: all run
all: bin/$(PACKAGE)/JettySim.class

bin/$(PACKAGE)/JettySim.class   : src/$(PACKAGE)/Operation.java
bin/$(PACKAGE)/JettySim.class   : src/$(PACKAGE)/devices/VGATextDevice.java
bin/$(PACKAGE)/JettySim.class   : src/$(PACKAGE)/JitRunner.java
bin/$(PACKAGE)/BasicBlock.class : src/$(PACKAGE)/MachineState.java
bin/$(PACKAGE)/BasicBlock.class : src/$(PACKAGE)/JettySim.java

bin:
	mkdir -p $@

bin/%.class: src/%.java | bin
	javac -d bin -cp bin -cp $(SOOTJARS) -sourcepath src $^

run: bin/$(PACKAGE)/JettySim.class
	java -classpath $(SOOTJARS):bin $(PACKAGE)/JettySim "$(RUNFILE)"

clean clobber:
	$(RM) -r bin
