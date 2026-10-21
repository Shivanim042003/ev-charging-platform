package com.evcharging.platform;

import com.evcharging.platform.station.Station;
import com.evcharging.platform.station.StationRepository;
import com.evcharging.platform.station.StationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class NearbyStationsTest {

    @Autowired StationRepository stationRepository;
    @Autowired StationService stationService;

    @Test
    void nearbyReturnsActiveStationsWithinRadiusNearestFirst() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Station mpls = stationRepository.save(new Station("Mpls " + tag, "a", 44.9778, -93.2650));
        Station stPaul = stationRepository.save(new Station("StPaul " + tag, "b", 44.9537, -93.0900));
        Station madison = stationRepository.save(new Station("Madison " + tag, "c", 43.0731, -89.4012));
        Station chicago = stationRepository.save(new Station("Chicago " + tag, "d", 41.8781, -87.6298));
        Station noCoords = stationRepository.save(new Station("NoCoords " + tag, "e"));
        Station inactive = stationRepository.save(new Station("Inactive " + tag, "f", 44.9800, -93.2600));
        inactive.deactivate();
        stationRepository.saveAndFlush(inactive);

        List<UUID> within20 = ids(stationService.findNearby(44.9778, -93.2650, 20));
        assertEquals(mpls.getId(), within20.get(0), "nearest first");
        assertTrue(within20.contains(stPaul.getId()));
        assertFalse(within20.contains(madison.getId()), "Madison is ~375 km away");
        assertFalse(within20.contains(noCoords.getId()), "no coordinates");
        assertFalse(within20.contains(inactive.getId()), "inactive stations are excluded");

        // Madison (~375 km) is inside 500 km, Chicago (~570 km) is not.
        List<UUID> within500 = ids(stationService.findNearby(44.9778, -93.2650, 500));
        assertTrue(within500.contains(madison.getId()));
        assertFalse(within500.contains(chicago.getId()));
        assertTrue(within500.indexOf(stPaul.getId()) < within500.indexOf(madison.getId()),
                "St. Paul before Madison");
    }

    @Test
    void nearbyRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> stationService.findNearby(91, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> stationService.findNearby(0, 181, 10));
        assertThrows(IllegalArgumentException.class, () -> stationService.findNearby(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> stationService.findNearby(0, 0, 501));
    }

    private static List<UUID> ids(List<Station> stations) {
        return stations.stream().map(Station::getId).toList();
    }
}
