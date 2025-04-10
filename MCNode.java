package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.ArrayList;

public class MCNode {
    private MCState state;
    private MCNode parent;
    private ArrayList<MCNode> children;
    private Integer visits;
    private double score;
    private Integer wins;


    public MCNode(MCState state, MCNode parent){
        this.state = state;
        this.parent = parent;
        this.children = new ArrayList<MCNode>();
        this.visits =  0;
        this.score = 0;
        this.wins = 0;
    }

    public Integer getWins(){
        return wins;
    }

    public void setWins(Integer num){
        wins+=num;
    }
    public MCState getState(){
        return state;
    }

    public void setState(MCState state){
        this.state = state;
    }

    public MCNode getParent(){
        return parent;
    }

    public void setParent(MCNode parent){
        this.parent = parent;
    }
    public ArrayList<MCNode> getChildren(){
        return children;
    }

    public void setChild(MCNode child){
        this.children.add(child);
    }
    public Integer getVisits(){
        return visits;
    }

    public void setVisits(Integer visits){
        this.visits = visits;
    }
    public double getScore(){
        return score;
    }

    public void setScore(double score){
        this.score = score;
    }
}
