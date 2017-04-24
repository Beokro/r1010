ESCAPE_HOME := ../

TARGETS := test_cliques_turan_shadow

OBJECTS := $(TARGETS:%=%.o)

include ../common.mk

INCLUDES += -I..
LDFLAGS += -L..

test_% : test_%.o ../libescape.a
	$(COMPILE_AND_LINK)
