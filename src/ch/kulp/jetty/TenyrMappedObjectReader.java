package ch.kulp.jetty;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.channels.FileChannel.MapMode.READ_ONLY;
import static java.nio.file.StandardOpenOption.READ;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class TenyrMappedObjectReader {
    public class Item {
        int offset;
    }

    public class SizedItem extends Item {
        int size;

        int[] asWords() {
            int[] result = new int[size];
            synchronized (buffer) {
                buffer.position(offset);
                buffer.asIntBuffer().get(result);
            }
            return result;
        }
    }

    public class Record extends SizedItem {
        int addr;
    }

    public class Symbol extends SizedItem {
        int flags;
        String name;
        int value;
    }

    public class Relocation extends Item {
        int flags;
        String name;
        int addr, width;
    }

    Path path;
    FileChannel channel;
    MappedByteBuffer buffer;
    int globalFlags;

    private int recordCount, symbolCount, relocationCount;
    private List<Record> records;
    private List<Symbol> symbols;
    private List<Relocation> relocations;

    public TenyrMappedObjectReader(Path path) throws IOException {
        super();
        this.path = path;
        channel = FileChannel.open(path, READ);
        buffer = channel.map(READ_ONLY, 0, channel.size());
        buffer.order(LITTLE_ENDIAN);
    }

    public void parse() throws ParseException, UnsupportedEncodingException {
        buffer.rewind();
        byte[] magic = new byte[3];
        buffer.get(magic);
        if (magic[0] != 'T' || magic[1] != 'O' || magic[2] != 'V')
            throw new ParseException("Object magic is wrong", buffer.arrayOffset());

        byte version = buffer.get();
        if (version != 0)
            throw new ParseException("Unsupported object version " + version, buffer.arrayOffset());

        globalFlags = buffer.getInt();

        parseRecords();
        parseSymbols();
        parseRelocations();
    }

    private void parseRelocations() throws UnsupportedEncodingException {
        relocationCount = buffer.getInt();
        relocations = new ArrayList<Relocation>(relocationCount);
        for (int i = 0; i < relocationCount; i++) {
            Relocation r = new Relocation();
            r.flags = buffer.getInt();
            byte[] nameBytes = new byte[32];
            buffer.get(nameBytes);
            r.name = new String(nameBytes, "US-ASCII");
            r.addr = buffer.getInt();
            r.width = buffer.getInt();
            r.offset = buffer.position();
            relocations.add(r);
        }
    }

    private void parseSymbols() throws UnsupportedEncodingException {
        symbolCount = buffer.getInt();
        symbols = new ArrayList<Symbol>(symbolCount);
        for (int i = 0; i < symbolCount; i++) {
            Symbol s = new Symbol();
            s.flags = buffer.getInt();
            byte[] nameBytes = new byte[32];
            buffer.get(nameBytes);
            s.name = new String(nameBytes, "US-ASCII");
            s.value = buffer.getInt();
            s.size = buffer.getInt();
            s.offset = buffer.position();
            symbols.add(s);
        }
    }

    private void parseRecords() {
        recordCount = buffer.getInt();
        records = new ArrayList<Record>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            Record r = new Record();
            r.addr = buffer.getInt();
            r.size = buffer.getInt();
            r.offset = buffer.position();
            buffer.position(r.offset + r.size * 4);
            records.add(r);
        }
    }

    public List<Record> getRecords() {
        return records;
    }

    public List<Symbol> getSymbols() {
        return symbols;
    }

    public List<Relocation> getRelocations() {
        return relocations;
    }
}
