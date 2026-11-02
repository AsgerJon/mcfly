package io.github.asgerjon.mcfly.unittas.tests;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Singleplayer hands packets to the other side as objects, never written out, so a packet's wire format goes untested
 * there. While installed on a connection's end, this writes each game packet arriving there to a buffer and reads it
 * back into a new packet, which goes on in its place, as over a real connection. A packet that fails to make the trip
 * goes on as it was, and the failure is kept for the test to report. Custom payloads (FML's) pass untouched.
 */
final class WireTap extends ChannelInboundHandlerAdapter {

	private static final String NAME = "mcfly:wiretap";

	/** The trips that failed since the last {@link #install}, as text. */
	static final List<String> FAILURES = Collections.synchronizedList(new ArrayList<>());

	/** Puts a tap at the front of the connection's incoming side, if there is none yet. */
	static void install(NetworkManager manager) {
		Channel channel = channel(manager);
		if (channel.pipeline().get(NAME) == null) {
			channel.pipeline().addFirst(NAME, new WireTap());
		}
	}

	static void remove(NetworkManager manager) {
		Channel channel = channel(manager);
		if (channel.pipeline().get(NAME) != null) {
			channel.pipeline().remove(NAME);
		}
	}

	/** The packet written out and read back into a new one of its class. */
	@SuppressWarnings("unchecked")
	static <P extends Packet> P copy(P packet) throws IOException {
		ByteBuf buffer = Unpooled.buffer();
		packet.writePacketData(new PacketBuffer(buffer));
		P read;
		try {
			read = (P) packet.getClass().newInstance();
		} catch (ReflectiveOperationException e) {
			throw new IOException("can't make a " + packet.getClass().getSimpleName(), e);
		}
		read.readPacketData(new PacketBuffer(buffer));
		if (buffer.readableBytes() != 0) {
			throw new IOException(packet.getClass().getSimpleName() + " left " + buffer.readableBytes() + " bytes unread");
		}
		return read;
	}

	@Override
	public void channelRead(ChannelHandlerContext context, Object message) {
		Object onward = message;
		if (message instanceof Packet && isGamePacket((Packet) message)) {
			try {
				onward = copy((Packet) message);
			} catch (Exception | Error e) {
				FAILURES.add(message.getClass().getSimpleName() + ": " + e);
			}
		}
		context.fireChannelRead(onward);
	}

	private static boolean isGamePacket(Packet packet) {
		return packet.getClass().getName().startsWith("net.minecraft.network.play.")
			&& !(packet instanceof S3FPacketCustomPayload) && !(packet instanceof C17PacketCustomPayload);
	}

	/** The connection's Netty channel: a private field of `NetworkManager`, found by its type. */
	private static Channel channel(NetworkManager manager) {
		for (Field field : NetworkManager.class.getDeclaredFields()) {
			if (field.getType() == Channel.class) {
				try {
					field.setAccessible(true);
					return (Channel) field.get(manager);
				} catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
			}
		}
		throw new IllegalStateException("NetworkManager has no Channel field");
	}
}
