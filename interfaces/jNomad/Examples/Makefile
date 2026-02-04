# Usage: make --eval="TARGET=OptimIBeam" build
# or
# Usage: make --eval="TARGET=Rosenbrock" build

ifndef $(NOMAD_HOME)
	NOMAD_HOME = /home/redonnet/Recherche/Code/Nomad4
endif

ifndef $(JAVA_HOME)
	JAVA_HOME = /usr/lib/jvm/java-21-openjdk-amd64
endif

JAVA_LIB_PATH = $(JAVA_HOME)/lib:$(NOMAD_HOME)/build/release/interfaces/jNomad

PATH = $(NOMAD_HOME)/build/release/interfaces/jNomad/

CLASSPATH = .:$(PATH)/jNomad.jar

LIBPATH = $(PATH)

build:
	$(JAVA_HOME)/bin/javac  -classpath $(CLASSPATH) $(TARGET).java
	
run:
	$(JAVA_HOME)/bin/java -cp $(CLASSPATH) -Djava.library.path=$(LIBPATH) $(TARGET)

clean:
	/usr/bin/rm -f *.class

