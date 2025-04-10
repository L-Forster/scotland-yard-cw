package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.ArrayList;
import java.util.List;

public class MCState {
    private List<Integer> playerLocations;

    public MCState(Integer mrxLocation,List<Integer>  detectiveLocations){
        playerLocations = new ArrayList<>();
        playerLocations.add(mrxLocation);
        playerLocations.addAll(1, detectiveLocations);

    }
    public List<Integer> getDetectiveLocations(){
        return playerLocations.subList(1,playerLocations.size());

    }
    public Integer getMRXLocation(){
        return playerLocations.get(0);
    }

    public List<Integer> getPlayerLocations(){
        return playerLocations;
    }

    public void setPlayerLocations(Integer index, Integer dest){
        playerLocations.set(index, dest);
    }
}
