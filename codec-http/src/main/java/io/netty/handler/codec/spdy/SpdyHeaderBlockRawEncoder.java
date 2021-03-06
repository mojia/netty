/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

import java.util.Set;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.*;

public class SpdyHeaderBlockRawEncoder extends SpdyHeaderBlockEncoder {

    private final int version;

    public SpdyHeaderBlockRawEncoder(int version) {
        if (version < SpdyConstants.SPDY_MIN_VERSION || version > SpdyConstants.SPDY_MAX_VERSION) {
            throw new IllegalArgumentException(
                    "unknown version: " + version);
        }
        this.version = version;
    }

    private void setLengthField(ByteBuf buffer, int writerIndex, int length) {
        if (version < 3) {
            buffer.setShort(writerIndex, length);
        } else {
            buffer.setInt(writerIndex, length);
        }
    }

    private void writeLengthField(ByteBuf buffer, int length) {
        if (version < 3) {
            buffer.writeShort(length);
        } else {
            buffer.writeInt(length);
        }
    }

    @Override
    public ByteBuf encode(ChannelHandlerContext ctx, SpdyHeadersFrame frame) throws Exception {
        Set<String> names = frame.headers().names();
        int numHeaders = names.size();
        if (numHeaders == 0) {
            return Unpooled.EMPTY_BUFFER;
        }
        if (numHeaders > SPDY_MAX_NV_LENGTH) {
            throw new IllegalArgumentException(
                    "header block contains too many headers");
        }
        ByteBuf headerBlock = Unpooled.buffer();
        writeLengthField(headerBlock, numHeaders);
        for (String name: names) {
            byte[] nameBytes = name.getBytes("UTF-8");
            writeLengthField(headerBlock, nameBytes.length);
            headerBlock.writeBytes(nameBytes);
            int savedIndex = headerBlock.writerIndex();
            int valueLength = 0;
            writeLengthField(headerBlock, valueLength);
            for (String value: frame.headers().getAll(name)) {
                byte[] valueBytes = value.getBytes("UTF-8");
                if (valueBytes.length > 0) {
                    headerBlock.writeBytes(valueBytes);
                    headerBlock.writeByte(0);
                    valueLength += valueBytes.length + 1;
                }
            }
            if (valueLength == 0) {
                if (version < 3) {
                    throw new IllegalArgumentException(
                            "header value cannot be empty: " + name);
                }
            } else {
                valueLength --;
            }
            if (valueLength > SPDY_MAX_NV_LENGTH) {
                throw new IllegalArgumentException(
                        "header exceeds allowable length: " + name);
            }
            if (valueLength > 0) {
                setLengthField(headerBlock, savedIndex, valueLength);
                headerBlock.writerIndex(headerBlock.writerIndex() - 1);
            }
        }
        return headerBlock;
    }

    @Override
    void end() {
    }
}
