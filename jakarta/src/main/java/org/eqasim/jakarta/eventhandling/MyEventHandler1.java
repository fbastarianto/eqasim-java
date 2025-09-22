package org.eqasim.jakarta.eventhandling;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Arrays;

public class MyEventHandler1 implements LinkEnterEventHandler {

	private final Id<Link> LINK_100680 = Id.create("100680", Link.class);
	private final Id<Link> LINK_199420 = Id.create("199420", Link.class);

	private Map<String, int[]> volumeLink100680;
	private Map<String, int[]> volumeLink199420;

	public MyEventHandler1() {
		reset(0);
	}

	// Hour bin: 0,1,2,... (explicit double division)
	private int getSlot(double time) {
		return (int) (time / 3600.0);
	}

	@Override
	public void reset(int iteration) {
		writeCsv(volumeLink100680, iteration + "_100680.csv");
		this.volumeLink100680 = new HashMap<>();
		writeCsv(volumeLink199420, iteration + "_199420.csv");
		this.volumeLink199420 = new HashMap<>();
	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
		// Derive mode from vehicle id (e.g. "123_car" -> "car")
		final String vehicleId = event.getVehicleId().toString();
		final String[] parts = vehicleId.split("_");
		final String mode = (parts.length == 1) ? "car" : parts[1];

		final int slot = getSlot(event.getTime());
		if (slot < 0) return; // safety

		if (event.getLinkId().equals(LINK_100680)) {
			int[] vol = getAndEnsure(volumeLink100680, mode, slot);
			vol[slot]++; // now always safe
		} else if (event.getLinkId().equals(LINK_199420)) {
			int[] vol = getAndEnsure(volumeLink199420, mode, slot);
			vol[slot]++;
		}
	}

	/**
	 * Ensure an int[] exists for this mode and is long enough to hold 'slot'.
	 * Starts at 24 bins but grows as needed (e.g., endTime > 24h).
	 */
	private static int[] getAndEnsure(Map<String, int[]> map, String mode, int slot) {
		int[] vol = map.get(mode);
		if (vol == null) {
			vol = new int[Math.max(24, slot + 1)];
			map.put(mode, vol);
			return vol;
		}
		if (slot >= vol.length) {
			int newLen = Math.max(slot + 1, (int) Math.ceil(vol.length * 1.5));
			vol = Arrays.copyOf(vol, newLen);
			map.put(mode, vol);
		}
		return vol;
	}

	private void writeCsv(Map<String, int[]> vol, String filename) {
		if (vol == null) return;
		try (BufferedWriter w = IOUtils.getBufferedWriter(filename)) {
			// inside writeCsv(...)
			w.write("mode,hour,clock_hour,day,vol\n");
			for (Entry<String, int[]> e : vol.entrySet()) {
				int[] arr = e.getValue();
				for (int hour = 0; hour < arr.length; hour++) {
					int clockHour = hour % 24;
					int day = hour / 24;      // 0 for 0–23, 1 for 24–47, etc.
					w.write(e.getKey() + "," + hour + "," + clockHour + "," + day + "," + arr[hour] + "\n");
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
}
