JAVAC = javac
JAVAFLAGS = -g
SRCDIR = src
BINDIR = bin
SOURCES := $(shell find $(SRCDIR) -name '*.java')
CLASSES = $(patsubst $(SRCDIR)/%.java,$(BINDIR)/%.class,$(SOURCES))

# Default target
all: $(CLASSES)

# Compile Java source files
$(BINDIR)/%.class: $(SRCDIR)/%.java
	mkdir -p $(BINDIR)
	$(JAVAC) $(JAVAFLAGS) -d $(BINDIR) $<

# Create output directory if it doesn't exist
$(BINDIR):
	mkdir -p $(BINDIR)

# Clean compiled files
clean:
	rm -rf $(BINDIR)
