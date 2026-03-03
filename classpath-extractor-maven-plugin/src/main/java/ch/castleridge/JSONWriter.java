package ch.castleridge;

import java.io.PrintWriter;
import java.util.function.Consumer;

public class JSONWriter {
    private PrintWriter writer;

    public JSONWriter(PrintWriter writer) {
        this.writer = writer;
    }

    public void close() {
        writer.close();
    }

    public void flush() {
        writer.flush();
    }

    public class ValueWriter {

        private String indent;
        ValueWriter(String indent) {
            this.indent = indent;
        }

        public void object(Consumer<PropertyWriter> writeProperties) {
            writer.println("{");
            writeProperties.accept(new PropertyWriter(indent+"  "));
            writer.println();
            writer.print(indent+"}");
        }

        public void array(Consumer<ArrayWriter> writeValues) {
            writer.println("[");
            writeValues.accept(new ArrayWriter(indent+"  "));
            writer.println();
            writer.print(indent+"]");
        }

        public void string(String value) {
            writer.print(indent+"\"" + value.replace("\\", "\\\\") + "\"");
        }
    }
    
    public class ArrayWriter {
        boolean first = true;
        private String indent;
        ArrayWriter(String indent) {
            this.indent = indent;
        }

        public void element(Consumer<ValueWriter> writeValue) {
            if (!first) {
                writer.println(",");
            }
            first = false;
            writeValue.accept(new ValueWriter(indent));
        }
    }

    public class PropertyWriter {
        private String indent;

        PropertyWriter(String indent) {
            this.indent = indent;
        }
        boolean first = true;
        public void property(String property, Consumer<ValueWriter> writeValue) {
            if (!first) {
                writer.println(",");
            }
            first = false;
            writer.print(indent+"\"" + property + "\": ");
            writeValue.accept(new ValueWriter(indent));
            
        }
    }

    public PropertyWriter object()  {
        return new PropertyWriter("");
    }
}