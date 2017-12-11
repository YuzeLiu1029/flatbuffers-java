package com.google.flatbuffers.grpc;

import com.google.flatbuffers.Table;
import io.grpc.Drainable;
import io.grpc.KnownLength;
import io.grpc.MethodDescriptor;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class FlatbuffersUtils {
    abstract public class FBExtactor  <T extends Table> {
        T extract (InputStream stream) throws IOException {
            if (stream instanceof KnownLength) {
                int size = stream.available();
                ByteBuffer buffer = ByteBuffer.allocate(size);
                stream.read(buffer.array());
                return extract(buffer);
            } else
                throw new RuntimeException("The class " + stream.getClass().getCanonicalName() + " does not extend from KnownLength ");
        }

        abstract T extract (ByteBuffer buffer);

    }

    static class FBInputStream extends InputStream implements Drainable, KnownLength {
        private final ByteBuffer buffer;
        private final int size;
        @Nullable private ByteArrayInputStream inputStream;

        FBInputStream(ByteBuffer buffer) {
            this.buffer = buffer;
            this.size = buffer.remaining();
        }

        private void makeStreamIfNotAlready() {
            if (inputStream == null)
                inputStream = new ByteArrayInputStream(buffer.array(), buffer.position(), size);
        }

        @Override
        public int drainTo(OutputStream target) throws IOException {
            target.write(buffer.array(), buffer.position(), size);
            return size;
        }

        @Override
        public int read() throws IOException {
            makeStreamIfNotAlready();
            return inputStream.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            makeStreamIfNotAlready();
            if (inputStream == null) {
                if (len >= size) {
                    System.arraycopy(buffer.array(), buffer.position(), b, off, size);
                    return size;
                } else {
                    makeStreamIfNotAlready();
                    return inputStream.read(b, off, len);
                }
            } else
                return inputStream.read(b, off, len);
        }

        @Override
        public int available() throws IOException {
            return inputStream == null ? size : inputStream.available();
        }

    }

    public static <T extends Table> MethodDescriptor.Marshaller<T> marshaller(final Class<T> clazz, final FBExtactor<T> extactor) {
        return new MethodDescriptor.ReflectableMarshaller<T>() {
            @Override
            public Class<T> getMessageClass() {
                return clazz;
            }

            @Override
            public InputStream stream(T value) {
                return new FBInputStream (value.getByteBuffer());
            }

            @Override
            public T parse(InputStream stream) {
                try {
                    return extactor.extract(stream);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
