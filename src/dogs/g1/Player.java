package dogs.g1;

import java.util.*;

import javax.swing.tree.AbstractLayoutCache.NodeDimensions;

// import org.graalvm.compiler.lir.aarch64.AArch64Unary.MemoryOp;

import java.lang.Math;

import dogs.sim.*;
import dogs.sim.Owner.OwnerName;
import dogs.sim.Directive.Instruction;
import dogs.sim.DogReference.Breed;


public class Player extends dogs.sim.Player {
    private List<ParkLocation> path;
    private Set<Owner> randos; 
    private List<Owner> nonRandos;
    private final Double MAX_THROW_DIST = 40.0;
    private HashMap<Owner, ParkLocation> ownerLocations;
    private List<Owner> ownerCycle;
    private int steppingStone;
    private HashMap<Integer, List<Owner>> teamOwners;
    private boolean waitToStart;
    private int currentThrow = 0;
    private boolean g4inPositionFirst;
    private boolean inCircuit = false;

    /**
     * Player constructor
     *
     * @param rounds           number of rounds
     * @param numDogsPerOwner  number of dogs per owner
     * @param numOwners	       number of owners
     * @param seed             random seed
     * @param simPrinter       simulation printer
     *
     */
     public Player(Integer rounds, Integer numDogsPerOwner, Integer numOwners, Integer seed, Random random, SimPrinter simPrinter) {
        super(rounds, numDogsPerOwner, numOwners, seed, random, simPrinter);
        this.path = new ArrayList<>();
        this.randos = new HashSet<Owner>();
        this.nonRandos = new ArrayList<>();
        this.ownerLocations = new HashMap<Owner, ParkLocation>();
        this.ownerCycle = new ArrayList<Owner>();
        this.steppingStone = 0;
        this.teamOwners = new HashMap<>();
        this.waitToStart = false;
        this.g4inPositionFirst = true;
        this.currentThrow = random.nextInt(numOwners);
     }

    /**
     * Choose command/directive for next round
     *
     * @param round        current round
     * @param myOwner      my owner
     * @param otherOwners  all other owners in the park
     * @return             a directive for the owner's next move
     *
     */
    public Directive chooseDirective(Integer round, Owner myOwner, List<Owner> otherOwners) {
        Directive directive = new Directive();
        if (round == 1) { // gets starting location, calls out name to find random players
            directive.instruction = Instruction.CALL_SIGNAL;
            directive.signalWord = "papaya";
            simPrinter.println(myOwner.getNameAsString() + " called out " + directive.signalWord + " in round " + round);
            return directive;
        }
        else if (round == 6) { // fills ups randos to spot the random player, make starting config with nonrandom players
            findRandos(myOwner, otherOwners);
            findTeamOwners(myOwner, otherOwners);
            if (teamOwners.get(1).size()>=15) {
                inCircuit = true;
                updateLocations(teamOwners.get(1).size(), false);
            }
            else
                updateLocations(teamOwners.get(1).size());
            
            this.path = shortestPath(ownerLocations.get(myOwner));
            simPrinter.println("It will take "  + myOwner.getNameAsString() + " " + this.path.size() + " rounds to get to target location");
        }

        updateTeamOwners(myOwner, otherOwners);
        float nodeSeparation = 2.0f;
        List<Owner> team1 = teamOwners.get(1);
        List<Owner> team3 = teamOwners.get(3);
        // special case collaboration with team 3
        if (team1.size()==1 && team3.size() >= 2) {
            ParkLocation target = moveCloserToCenterOtherOwners(team3);
            ParkLocation myLoc = myOwner.getLocation();
            
            if ((myLoc.getRow().equals(target.getRow())) && myLoc.getColumn().equals(target.getColumn())) {
                List<Dog> waitingDogs = myDogsWaiting(myOwner);
                waitingDogs.addAll(getWaitingDogs(myOwner, otherOwners));
                if (waitingDogs.size() == 0 || round % 10 != 1) {
                    directive.instruction = Instruction.NOTHING;
                    return directive;
                }
                directive.dogToPlayWith = waitingDogs.get(0);
                directive.instruction = Instruction.THROW_BALL;
                List<Owner> closestOwners = team3;
                directive.parkLocation = getLeastBusy(closestOwners).getLocation();
                if (round % 10 == 1) return directive;
            }
            else {
                directive.instruction = Instruction.MOVE;
                directive.parkLocation = moveCloserToCenterOtherOwners(team3);
                return directive;
            }
        }

        // special case collaboration with team 4
        List<Owner> team4 = teamOwners.get(4);
        if (team1.size()==1 && team4.size() >= 2) {
            ParkLocation myLoc = myOwner.getLocation();
            ParkLocation target = getThirdVertex(team4.get(0).getLocation(), team4.get(1).getLocation(), myLoc, 40.0, 40.0);
            
            if ((myLoc.getRow().equals(target.getRow())) && myLoc.getColumn().equals(target.getColumn())) {
                if (g4inPositionFirst) {
                    Collections.sort(team4, new Comparator<Owner>() {
                        @Override public int compare(Owner o1, Owner o2) {
                            return o1.getNameAsString().compareTo(o2.getNameAsString());
                        }
                    });
                    directive.instruction = Instruction.CALL_SIGNAL;
                    directive.signalWord = team4.get(0).getNameAsString();
                    int count = 0;
                    for (Owner owner : team4) {
                        if (!owner.getCurrentSignal().equals("ready")) {
                            count++;
                        }
                    }
                    if (count == 1 || round > 150)
                        g4inPositionFirst = false;
                    return directive;
                }
                List<Dog> waitingDogs = myDogsWaiting(myOwner);
                waitingDogs.addAll(getWaitingDogs(myOwner, otherOwners));
                if (waitingDogs.size() == 0) {
                    directive.instruction = Instruction.NOTHING;
                    return directive;
                }
                directive.dogToPlayWith = waitingDogs.get(0);
                directive.instruction = Instruction.THROW_BALL;
                directive.parkLocation = team4.get(1).getLocation();
                return directive;
            }
            else {
                directive.instruction = Instruction.MOVE;
                directive.parkLocation = getThirdVertex(team4.get(0).getLocation(), team4.get(1).getLocation(), myLoc, 40.0, 40.0);
                return directive;
            }
        }

        // if not at intended location
        if (steppingStone != path.size()) {
            directive.instruction = Instruction.MOVE;
            directive.parkLocation = this.path.get(steppingStone++);
            return directive;
        }

        // TODO: stay away from the random/deal with random
        updateRandos(myOwner, otherOwners);
        updateWaiting();

        // TODO: do something while waiting for the rest to get into position. Maybe throw to the center? 
        if (!inCircuit && !waitToStart) {
            simPrinter.println(myOwner.getNameAsString() + " is at target location");
            directive.instruction = Instruction.CALL_SIGNAL;
            directive.signalWord = "here";
            return directive;
        }

        // when using the circuit 
        if (inCircuit && getAllWaitingDogs(myOwner, otherOwners).size() != 0) {
            Owner ow = someoneSaid(myOwner, otherOwners, "lonely");
            if (ow != null) {
                currentThrow = (currentThrow + 1)%ownerCycle.size();
                return throwToLocation(myOwner, ow.getLocation(), nodeSeparation, otherOwners);
            }
            while (true) {
                if (distanceBetweenTwoPoints(ownerCycle.get(currentThrow).getLocation(), myOwner.getLocation()) <= 40)
                    return throwToLocation(myOwner, ownerCycle.get(currentThrow).getLocation(), nodeSeparation, otherOwners);
                currentThrow = (currentThrow + 1)%ownerCycle.size();
            }
        }

        // OPTION: change how far each node is from the other one in the isosceles triangle
        if (!checkTooFarFromOtherOwners(myOwner, otherOwners, 40.0)) {  // if not too far from all other owners, start throwing
            Collections.sort(team1, new Comparator<Owner>() {
                @Override public int compare(Owner o1, Owner o2) {
                    return o1.getNameAsString().compareTo(o2.getNameAsString());
                }
            });
            if (otherOwners.size()>0 && team1.size()==1) {
                List<Owner> closestOwners = new ArrayList<>();
                closestOwners.add(getClosestOwner(myOwner, otherOwners));
                return throwToNext(myOwner, closestOwners, nodeSeparation, otherOwners);
            }
            else if (team1.size() >= 2 && team4.size() == 1 && team1.get(0).getNameAsString().equals(myOwner.getNameAsString())) {
                Owner g4Owner = team4.get(0);
                if (g4inPositionFirst && g4Owner.getCurrentSignal().equals(myOwner.getNameAsString())) {
                    g4inPositionFirst = false;
                }
                if (!g4inPositionFirst) {
                    List<Dog> waitingDogs = myDogsWaiting(myOwner);
                    waitingDogs.addAll(getWaitingDogs(myOwner, otherOwners));
                    if (waitingDogs.size() == 0) {
                        directive.instruction = Instruction.NOTHING;
                        return directive;
                    }
                    directive.dogToPlayWith = waitingDogs.get(0);
                    directive.instruction = Instruction.THROW_BALL;
                    directive.parkLocation = g4Owner.getLocation();
                    return directive;
                }
                return throwToNext(myOwner, otherOwners, nodeSeparation, otherOwners);
            }
            else {
                return throwToNext(myOwner, otherOwners, nodeSeparation, otherOwners);
            }
        }
        else {  // if too far, move closer to the closest owner
            directive.instruction = Instruction.MOVE;
            directive.parkLocation = moveCloserToOtherOwners(myOwner, otherOwners);
            return directive;
        }
    }

    /** 
     *  Throws to the next owner in the geometry OR an owner within 40 m (hopefully not random)
     *  @param A                myOwner, the thrower of the ball 
     *  @param nodeSeparation   how far in between each circle on the top of the isosceles triangle 
     *                          Min: 0      Max: 2 
     */
    private Directive throwToNext(Owner A, List<Owner> otherOwners, float nodeSeparation, List<Owner> allOtherOwners) {
        // TODO: maybe throw in another direction to avoid overwhelming someone? 
        Owner B = new Owner(); // the next owner to trow to, determined later by who is available 

        Directive ret = new Directive();
        ret.instruction = Instruction.THROW_BALL;

        // TODO: Prioritize random dogs?
        // pick which Node to throw to (0 = right at next owner, 3 = farthest possible from owner)
        List<Dog> waitingDogs = myDogsWaiting(A);
        
        if (waitingDogs.size() == 0) // TODO: call out something to say you have no dogs? 
            waitingDogs = getWaitingDogs(A, allOtherOwners);

        if (waitingDogs.size() == 0) {
            simPrinter.println("There are no waiting dogs for " + A.getNameAsString());
            ret.instruction = Instruction.CALL_SIGNAL;
            ret.signalWord = "lonely";
            return ret;
        }
        
        ret.dogToPlayWith = waitingDogs.get(0); 
        int N = getNodeForDog(waitingDogs, ret.dogToPlayWith);
        float offset = N + N*nodeSeparation; // distance between node and next Owner (top of isosceles)
        
        boolean foundTarget = false; 
        // if only one team 1 player and multiple other team players
        if (otherOwners.size()>0 && teamOwners.get(1).size()==1) {
            B = otherOwners.get(0);
            if (distanceBetweenTwoPoints(A.getLocation(), B.getLocation()) <= 40)
                foundTarget = true;
        }
        else if (nonRandos.size() >= 2) { // we can throw to someone else that's smart!  
            // pick the next person in the cycle, ensuring that they fall within 40 meters

            // for (Owner o : nonRandos) 
            //     simPrinter.println("Owner: " + o.getNameAsString() + "\tLocation: " + o.getLocation());

            B = ownerCycle.get((findOwnerIndex(ownerCycle,A) + 1) % ownerCycle.size());
            if (distanceBetweenTwoPoints(A.getLocation(), B.getLocation()) > 40) {
                for (Owner o : nonRandos) {
                    if (o == A) continue; 
                    B = o; 
                    if (distanceBetweenTwoPoints(A.getLocation(), B.getLocation()) <= 40) {
                        foundTarget = true;
                        break;
                    }
                }
            }
            else 
                foundTarget = true;
        }
        else if (randos.size() >= 1 && !(foundTarget)) { // we have to trow to a rando :/ 
            // pick the next available person within 40 meters 
            for (Owner o : randos) {
                B = o; 
                if (distanceBetweenTwoPoints(A.getLocation(), B.getLocation()) <= 40) break;
            }
        }
        else { // case where nobody else is within the range
            // throw in a random direction just to get some exercise? 
            return randomThrow(A, ret);
        }

        Double Ax = A.getLocation().getRow();
        Double Ay = A.getLocation().getColumn();
        Double Bx = B.getLocation().getRow();
        Double By = B.getLocation().getColumn();
        ParkLocation newB = new ParkLocation(Bx, By);

        // distance between thrower and receiver (matching sides of isosceles), maximum = 40m 
        double throwDistance = distanceBetweenTwoPoints(A.getLocation(), B.getLocation());
        
        // OPTION: change side of owner the throw is headed
        // double theta = -1 * Math.asin((offset/2)/throwDistance) * 2; 
        double theta = Math.asin((offset/2)/throwDistance) * 2; 

        // Apply translation, rotation, translation to rotate about non-origin
        newB.setRow(Ax + (Bx-Ax)*Math.cos(theta) - (By-Ay)*Math.sin(theta));
        newB.setColumn(Ay + (Bx-Ax)*Math.sin(theta) + (By-Ay)*Math.cos(theta));

        simPrinter.println("\nThrowing from " + A.getNameAsString() + " to " + B.getNameAsString());
        simPrinter.println("Point A: " + A.getLocation() + "\tPoint B: " + newB + "\n");
        ret.parkLocation = newB;
        return ret;
    }

    private Directive throwToLocation(Owner A, ParkLocation B, float nodeSeparation, List<Owner> allOtherOwners) {
        Directive ret = new Directive();
        ret.instruction = Instruction.THROW_BALL;
        List<Dog> waitingDogs = myDogsWaiting(A);
        
        if (waitingDogs.size() == 0) // TODO: call out something to say you have no dogs? 
            waitingDogs = getWaitingDogs(A, allOtherOwners);

        if (waitingDogs.size() == 0) {
            simPrinter.println("There are no waiting dogs for " + A.getNameAsString());
            ret.instruction = Instruction.NOTHING;
            return ret;
        }
        
        ret.dogToPlayWith = waitingDogs.get(0); 
        int N = getNodeForDog(waitingDogs, ret.dogToPlayWith);
        float offset = N + N*nodeSeparation; // distance between node and next Owner (top of isosceles)

        Double Ax = A.getLocation().getRow();
        Double Ay = A.getLocation().getColumn();
        Double Bx = B.getRow();
        Double By = B.getColumn();
        ParkLocation newB = new ParkLocation(Bx, By);

        // distance between thrower and receiver (matching sides of isosceles), maximum = 40m 
        double throwDistance = distanceBetweenTwoPoints(A.getLocation(), B);
        
        // OPTION: change side of owner the throw is headed
        // double theta = -1 * Math.asin((offset/2)/throwDistance) * 2; 
        double theta = Math.asin((offset/2)/throwDistance) * 2; 

        // Apply translation, rotation, translation to rotate about non-origin
        newB.setRow(Ax + (Bx-Ax)*Math.cos(theta) - (By-Ay)*Math.sin(theta));
        newB.setColumn(Ay + (Bx-Ax)*Math.sin(theta) + (By-Ay)*Math.cos(theta));
        ret.parkLocation = newB;
        return ret;
    }

    private Directive throwToNext(Owner A, Owner B, float nodeSeparation, List<Owner> allOtherOwners) {
        List<Owner> ownerList = new ArrayList<>();
        ownerList.add(B);
        return throwToNext(A, ownerList, nodeSeparation, allOtherOwners);
    }

    private int findOwnerIndex(List<Owner> haystack, Owner needle) {
        for (int i = 0; i < haystack.size(); i++) {
            if (haystack.get(i).getNameAsString().equals(needle.getNameAsString()))
                return i;
        }
        return -1; 
    }

    private Owner findOwner(List<Owner> haystack, Owner needle) {
        for (int i = 0; i < haystack.size(); i++) {
            if (haystack.get(i).getNameAsString().equals(needle.getNameAsString()))
                return haystack.get(i);
        }
        return null; 
    }

    private void updateWaiting() {
        boolean everyoneSignaledHere = true;
        for (Owner person : teamOwners.get(1)) {
            // simPrinter.println(person.getNameAsString() + " did " + person.getCurrentAction()); 
            String signal = person.getCurrentSignal();
            if (person.getCurrentAction() == Instruction.THROW_BALL)
                waitToStart = true;
            if (person.getCurrentAction() != Instruction.CALL_SIGNAL && signal != null && !signal.isEmpty() && !signal.equals("here"))
                everyoneSignaledHere = false;
        }
        if (everyoneSignaledHere)
            waitToStart = true;
    }

    private void updateRandos(Owner me, List<Owner> otherOwners) {
        Set<Owner> newRandos = new HashSet<Owner>();
        List<Owner> newOwnerCycle = new ArrayList<>(); 
        List<Owner> newNonRandos = new ArrayList<>();
        newNonRandos.add(me);
        for (Owner o : nonRandos) {
            if (o.getNameAsString().equals(me.getNameAsString())) continue;
            newNonRandos.add(findOwner(otherOwners, o));
        }
        for (Owner o : randos) {
            newRandos.add(findOwner(otherOwners, o));
        }
        for (Owner o : ownerCycle) {
            if (o.getNameAsString().equals(me.getNameAsString())) 
                newOwnerCycle.add(me);
            else
                newOwnerCycle.add(findOwner(otherOwners, o));
        }
        this.randos = newRandos;
        this.nonRandos = newNonRandos; 
        this.ownerCycle = newOwnerCycle;
    }


    /**
     * Get the location where the current player will move to in the circle
     */
    private void updateLocations(int numOwners) {
        double dist = 39.0;     // use 40 for now
        double fromEdges = 10.0; // how far from the edges of the park 
        // OPTION: change dist and fromEdges to change the shape
        List<ParkLocation> optimalStartingLocations = getOptimalLocationShape(numOwners, dist, fromEdges);
        List<Owner> g1 = teamOwners.get(1);
   
        Collections.sort(g1, new Comparator<Owner>() {
            @Override public int compare(Owner o1, Owner o2) {
                return o1.getNameAsString().compareTo(o2.getNameAsString());
            }
        });

        // add cycle to array and to tracker for locations 
        for (int i = 0; i < numOwners; i++) {
            ownerLocations.put(g1.get(i), optimalStartingLocations.get(i));
            ownerCycle.add(g1.get(i));
        }
        // OPTION: change the cycle direction
        // Collections.reverse(ownerCycle);
    }

    private void updateLocations(int numOwners, boolean circuit) {
        double dist = 39;     // use 40 for now
        double fromEdges = 10.0; // how far from the edges of the park 
        // OPTION: change dist and fromEdges to change the shape
        List<ParkLocation> optimalStartingLocations = formNetwork(numOwners, dist, fromEdges);
   
        Collections.sort(nonRandos, new Comparator<Owner>() {
            @Override public int compare(Owner o1, Owner o2) {
                return o1.getNameAsString().compareTo(o2.getNameAsString());
            }
        });

        // add cycle to array and to tracker for locations 
        for (int i = 0; i < numOwners; i++) {
            ownerLocations.put(nonRandos.get(i), optimalStartingLocations.get(i));
            ownerCycle.add(nonRandos.get(i));
        }
        // OPTION: change the cycle direction
        // Collections.reverse(ownerCycle);
    }

    private void findRandos(Owner myOwner, List<Owner> otherOwners) {
        nonRandos.add(myOwner);
        List<String> teams = new ArrayList<String>(Arrays.asList("papaya", "two", "three", "zythum", "Zyzzogeton"));

        for (Owner person : otherOwners) {
            String signal = person.getCurrentSignal();
            if (signal != null && !signal.isEmpty() && teams.contains(signal)) {
                nonRandos.add(person);     
            }
            else {
                randos.add(person);
            }
        }
        for (Owner person : randos)
            simPrinter.println(person.getNameAsString() + " is a random player");
    }

    private void findTeamOwners(Owner myOwner, List<Owner> otherOwners) {
        List<String> teams = new ArrayList<String>(Arrays.asList("papaya", "two", "three", "zythum", "Zyzzogeton"));
        teamOwners.put(2, new ArrayList<Owner>());
        teamOwners.put(3, new ArrayList<Owner>());
        teamOwners.put(4, new ArrayList<Owner>());
        teamOwners.put(5, new ArrayList<Owner>());
        List<Owner> temp = new ArrayList<Owner>();
        temp.add(myOwner);
        teamOwners.put(1, temp);

        for (Owner person : otherOwners) {
            String signal = person.getCurrentSignal();
            if (signal != null && !signal.isEmpty() && teams.contains(signal)) {
                teamOwners.get(teams.indexOf(signal) + 1).add(person);        
            }
        }
    }

    private void updateTeamOwners(Owner myOwner, List<Owner> otherOwners) {
        HashMap<Integer, List<Owner>> tempMap = new HashMap<>();
        tempMap.put(2, new ArrayList<Owner>());
        tempMap.put(3, new ArrayList<Owner>());
        tempMap.put(4, new ArrayList<Owner>());
        tempMap.put(5, new ArrayList<Owner>());
        List<Owner> temp = new ArrayList<Owner>();
        temp.add(myOwner);
        tempMap.put(1, temp);

        for (int key : teamOwners.keySet()) {
            for (Owner owner : teamOwners.get(key)) {
                for (Owner otherOwner : otherOwners) {
                    if (otherOwner.getNameAsString().equals(owner.getNameAsString()))
                        tempMap.get(key).add(otherOwner); 
                }
            }
        }
        teamOwners = tempMap;
    }

    private Owner getLeastBusy(List<Owner> otherOwners) {
        int n = Integer.MAX_VALUE;
        Owner leastBusy = new Owner();
        for (Owner owner : otherOwners) {
            int dogsWaiting = getWaitingDogs(owner, otherOwners).size();
            dogsWaiting += myDogsWaiting(owner).size();
            if (dogsWaiting < n) {
                n = dogsWaiting;
                leastBusy = owner;
            }
        }
        return leastBusy;
    }

    private Directive randomThrow(Owner myOwner, Directive directive) {
        ParkLocation ret = new ParkLocation(); 
        while (distanceBetweenTwoPoints(ret, myOwner.getLocation()) < 40) { 
            double randomAngle = Math.toRadians(random.nextDouble() * 360);
            double ballRow = myOwner.getLocation().getRow() + 40.0 * Math.sin(randomAngle);
            double ballColumn = myOwner.getLocation().getColumn() + 40.0 * Math.cos(randomAngle);
            if(ballRow < 0.0) ballRow = 0.0;
            if(ballRow > ParkLocation.PARK_SIZE - 1) ballRow = ParkLocation.PARK_SIZE - 1;
            if(ballColumn < 0.0) ballColumn = 0.0;
            if(ballColumn > ParkLocation.PARK_SIZE - 1) ballColumn = ParkLocation.PARK_SIZE - 1;
            ret = new ParkLocation(ballRow, ballColumn); 
            directive.parkLocation = ret;
        }
        simPrinter.println(myOwner.getNameAsString() + " had to do a random throw that was " + distanceBetweenTwoPoints(ret, myOwner.getLocation()) + " meters long");
        return directive;
    }

    /**
     * Get the optimal shape located closest to the park gates
     *
     * @param n            number of players
     * @param dist         distance between each player
     * @param fromEdges    distance between player and gate 
     * @return             list of park locations where each player should go
     *
     */
    private List<ParkLocation> getOptimalLocationShape(Integer n, Double dist, Double fromEdges) {
        List<ParkLocation> shape = new ArrayList<ParkLocation>();
        if (n == 1)
            shape.add(new ParkLocation(fromEdges, fromEdges));
        else if (n == 2) {
            double radian = Math.toRadians(45.0);
            shape.add(new ParkLocation(fromEdges+Math.cos(radian)*dist, fromEdges));
            shape.add(new ParkLocation(fromEdges, fromEdges+Math.cos(radian)*dist));
        }
        else if (n == 3) {
            double radian1 = Math.toRadians(-15.0);
            double radian2 = Math.toRadians(-75.0);
            shape.add(new ParkLocation(fromEdges, fromEdges));
            shape.add(new ParkLocation(fromEdges+Math.cos(radian1)*dist, fromEdges-Math.sin(radian1)*dist));
            shape.add(new ParkLocation(fromEdges+Math.cos(radian2)*dist, fromEdges-Math.sin(radian2)*dist));
        }
        else if (n == 4) {
            shape.add(new ParkLocation(fromEdges,fromEdges));
            shape.add(new ParkLocation(fromEdges+dist,fromEdges));
            shape.add(new ParkLocation(fromEdges+dist,fromEdges+dist));
            shape.add(new ParkLocation(fromEdges,fromEdges+dist));
        }
        else {
            double radianStep = Math.toRadians(360.0/n);
            double radius = (dist/2)/(Math.sin(radianStep/2));
            double center = fromEdges+radius;
            double radian = Math.toRadians(135.0);
            for (int i = 0; i < n; i++) {
                double x = Math.cos(radian) * radius + center;
                double y = Math.sin(radian) * radius + center;
                shape.add(new ParkLocation(x,y));
                radian -= radianStep;
            }
        }
        return shape;
    }

    private List<ParkLocation> formNetwork(Integer n, Double dist, Double fromEdges) {
        List<ParkLocation> shape = new ArrayList<ParkLocation>();
        double delt = Math.sqrt(Math.pow(dist, 2) - Math.pow(dist/2, 2));
        ParkLocation[] layout = new ParkLocation[] {
            new ParkLocation(fromEdges, fromEdges ), // 1 
            new ParkLocation(fromEdges + dist, fromEdges ), // 2
            new ParkLocation(fromEdges + dist/2, fromEdges + delt ), // 3
            new ParkLocation(fromEdges + dist*1.5, delt + fromEdges ), // 4 
            new ParkLocation(fromEdges + dist*2, fromEdges ), // 5 
            new ParkLocation(fromEdges + dist*2.5, delt + fromEdges ), // 6 
            new ParkLocation(fromEdges + dist, 2*delt + fromEdges ), // 7 
            new ParkLocation(fromEdges + dist*2, 2*delt + fromEdges ), // 8 
            new ParkLocation(fromEdges, 2*delt + fromEdges ), // 9 
            new ParkLocation(fromEdges + dist*3, 2*delt + fromEdges ), // 10
            new ParkLocation(fromEdges + dist*1.5, 3*delt + fromEdges ), // 11
            new ParkLocation(fromEdges + dist*2.5, 3*delt + fromEdges ), // 12
            new ParkLocation(fromEdges + dist/2, 3*delt + fromEdges ), // 13
            new ParkLocation(fromEdges + dist*3, fromEdges ), // 14
            new ParkLocation(fromEdges + dist*2, 4*delt + fromEdges ), // 15
            new ParkLocation(fromEdges + dist*3.5, 3*delt + fromEdges ), // 16
            new ParkLocation(fromEdges + dist*3, 4*delt + fromEdges ), // 17
            new ParkLocation(fromEdges + dist, 4*delt + fromEdges ), // 18
            new ParkLocation(fromEdges + dist*3.5, delt + fromEdges), // 19
            new ParkLocation(fromEdges, 4*delt + fromEdges ), // 20
            new ParkLocation(fromEdges + dist*4, 4*delt + fromEdges), // 21
            new ParkLocation(fromEdges + dist/2, 5*delt + fromEdges ), // 22
            new ParkLocation(fromEdges + dist*1.5, 5*delt + fromEdges ), // 23
            new ParkLocation(fromEdges + dist*2.5, 5*delt + fromEdges ), // 24
            new ParkLocation(fromEdges + dist*3.5, 5*delt + fromEdges ) // 25 
        };
        for (int i = 1; i <= n; i++) {
            shape.add(layout[i-1]);
        }
        return shape;
    }

    /**
     * Check if owner is too far from other owners
     *
     * @param myOwner      my owner
     * @param otherOwners  list of other owners
     * @return             true if too far from all owners, false if not
     *
     */
    private boolean checkTooFarFromOtherOwners(Owner myOwner, List<Owner> otherOwners, double dist) {
        if (otherOwners.size() == 0)
            return false;
        ParkLocation myLoc = myOwner.getLocation();
        for (Owner owner : otherOwners) {
            ParkLocation otherLoc = owner.getLocation();
            if (distanceBetweenTwoPoints(myLoc, otherLoc) <= dist)
                return false;
        }
        return true;
    }

    /**
     * Get other owner closest to this owner
     *
     * @param myOwner      my owner
     * @param otherOwners  list of other owners
     * @return             true if too far from all owners, false if not
     *
     */
    private Owner getClosestOwner(Owner myOwner, List<Owner> otherOwners) {
        double dist = Double.MAX_VALUE;
        ParkLocation myLoc = myOwner.getLocation();
        Owner closestOwner = new Owner();
        for (Owner owner : otherOwners) {
            ParkLocation otherLoc = owner.getLocation();
            double newDist = distanceBetweenTwoPoints(myLoc, otherLoc);
            if (newDist < dist) {
                dist = newDist;
                closestOwner = owner;
            }
        }
        return closestOwner;
    }

    private List<Owner> getCloseOwners(Owner myOwner, List<Owner> otherOwners) {
        double dist = Double.MAX_VALUE;
        List<Owner> circuit = new ArrayList<>();
        ParkLocation myLoc = myOwner.getLocation();
        Owner closestOwner = new Owner();
        for (Owner owner : otherOwners) {
            ParkLocation otherLoc = owner.getLocation();
            double newDist = distanceBetweenTwoPoints(myLoc, otherLoc);
            if (newDist < dist) {
                circuit = new ArrayList<>();
                circuit.add(owner);
                dist = newDist;
            }
            else if (newDist == dist)
                circuit.add(owner);
        }
        return circuit;
    }

    /**
     * Move closer to the closest owner
     *
     * @param myOwner      my owner
     * @param otherOwners  list of other owners
     * @return             park location to move to
     *
     */
    private ParkLocation moveCloserToOtherOwners(Owner myOwner, List<Owner> otherOwners) {
        ParkLocation myLoc = myOwner.getLocation();
        Owner closestOwner = getClosestOwner(myOwner, otherOwners);
        ParkLocation target = closestOwner.getLocation();
        return getNextMove(myLoc, target);
    }

    private ParkLocation getNextMove(ParkLocation myLoc, ParkLocation target) {
        double magnitude = distanceBetweenTwoPoints(myLoc, target);
        if (magnitude <= 5)
            return target;
        double x = ((target.getRow()-myLoc.getRow())*5/magnitude)+myLoc.getRow();
        double y = ((target.getColumn()-myLoc.getColumn())*5/magnitude)+myLoc.getColumn();
        return new ParkLocation(x,y);
    }

    /**
     * Move closer to the closest owner
     *
     * @param myOwner      my owner
     * @param otherOwners  list of other owners
     * @return             center of owner circle, park location to move to
     *
     */
    private ParkLocation moveCloserToCenterOtherOwners(List<Owner> otherOwners) {
        double x = 0;
        double y = 0;
        int n = otherOwners.size();
        for (Owner owner : otherOwners) {
            x += owner.getLocation().getRow();
            y += owner.getLocation().getColumn();
        }
        return new ParkLocation(x/n,y/n);
    }

    private Owner someoneSaid(Owner myOwner, List<Owner> otherOwners, String saying) {
        for (Owner person : otherOwners) {
            String signal = person.getCurrentSignal();
            if (person.getCurrentAction() == Instruction.CALL_SIGNAL && signal != null && !signal.isEmpty() && signal.equals(saying) && distanceBetweenTwoPoints(myOwner.getLocation(), person.getLocation()) <= 40)
                return person;
        }
        return null;
    }

    /**
     * Get the shortest path to starting point along which player will move
     *
     * @param start        starting point
     * @return             list of park locations along which owner will move to get to starting point
     *
     */
    private List<ParkLocation> shortestPath(ParkLocation start) {
        List<ParkLocation> p = new ArrayList<>();
        double magnitude = euclideanDistance(start.getRow(), start.getColumn());
        if (magnitude == 0)
            return p;
        
        double xStep = start.getRow()/magnitude;
        double yStep = start.getColumn()/magnitude;
        double xTemp = xStep*5;
        double yTemp = yStep*5;
        while (xTemp <= start.getRow() && yTemp <= start.getColumn()) {
            p.add(new ParkLocation(xTemp, yTemp));
            xTemp += xStep*5;
            yTemp += yStep*5;
        }
        p.add(start);
        return p;
    }

    private double euclideanDistance(double x, double y) {
        return Math.sqrt(Math.pow(x,2)+Math.pow(y,2));
    }

    private Double distanceBetweenTwoPoints(ParkLocation p1, ParkLocation p2) {
        Double x1 = p1.getColumn();
        Double y1 = p1.getRow();
        Double x2 = p2.getColumn();
        Double y2 = p2.getRow();
        return euclideanDistance(x1-x2, y1-y2);
    }

    private ParkLocation getThirdVertex(ParkLocation v1, ParkLocation v2, ParkLocation v3, Double d1, Double d2) {
        Double dV = distanceBetweenTwoPoints(v1, v2);
        Double d12 = Math.pow(d1, 2);
        Double d22 = Math.pow(d2, 2);
        Double dV2 = Math.pow(dV, 2);
        Double phi1 = Math.atan2(v2.getColumn()-v1.getColumn(), v2.getRow()-v1.getRow());
        Double phi2 = Math.acos((d12+dV2-d22)/(2*d1*dV));

        Double x = v1.getRow()+d1*Math.cos(phi1-phi2);
        Double y = v1.getColumn()+d1*Math.sin(phi1-phi2);

        if (x < 0 || y < 0 || x > 200 || y > 200) {
            x = v1.getRow()+d1*Math.cos(phi1+phi2);
            y = v1.getColumn()+d1*Math.sin(phi1+phi2);
        }

        return getNextMove(v3, new ParkLocation(x, y));
    }
  
    /**
     * Returns a list of dogs waiting for myOwner, 
     * sorted by decreasing amount of time left to wait 
     * 
     * @param myOwner
     * @param otherOwners
     * @return
     */
    private List<Dog> getWaitingDogs(Owner myOwner, List<Owner> otherOwners) {
        List<Dog> waitingDogs = new ArrayList<>();
    	for(Owner otherOwner : otherOwners) {
    		for(Dog dog : otherOwner.getDogs()) {
    			if(dog.isWaitingForOwner(myOwner))
    				waitingDogs.add(dog);
    		}
    	}
        Collections.sort(waitingDogs, new Comparator<Dog>() {
            @Override public int compare(Dog d1, Dog d2) {
                return d1.getWaitingTimeRemaining().compareTo(d2.getWaitingTimeRemaining());
            }
        });
        return waitingDogs;
    }

    private List<Dog> getAllWaitingDogs(Owner myOwner, List<Owner> otherOwners) {
        List<Dog> mine = myDogsWaiting(myOwner);
        List<Dog> theirs = getWaitingDogs(myOwner, otherOwners);
        List<Dog> all = new ArrayList<>();
        all.addAll(mine);
        all.addAll(theirs);
        return all;
    }

    private List<Dog> myDogsWaiting(Owner myOwner) { 
        List<Dog> waitingDogs = new ArrayList<>();
    	for(Dog dog : myOwner.getDogs()) {
    		if(dog.isWaitingForOwner(myOwner))
    			waitingDogs.add(dog);
        }
        Collections.sort(waitingDogs, new Comparator<Dog>() {
            @Override public int compare(Dog d1, Dog d2) {
                return d1.getExerciseTimeCompleted().compareTo(d2.getExerciseTimeCompleted());
            }
        });

        // dont exercise dogs that are already exercised
        Iterator<Dog> itr = waitingDogs.iterator();
        while (itr.hasNext()) {
            Dog d = itr.next();
            if (d.getExerciseTimeRemaining() == 0.0) {
                itr.remove();
            }
        }
        return waitingDogs;
    } 
    
    private Integer getNodeForDog(List<Dog> waitingDogs, Dog dog) {
        Set<DogReference.Breed> waitingBreeds = new HashSet<>();
        for (Dog waitingDog: waitingDogs) {
            waitingBreeds.add(waitingDog.getBreed());
        }

        ArrayList<DogReference.Breed> breedsBySpeed = new ArrayList<>();
        breedsBySpeed.add(DogReference.Breed.TERRIER);
        breedsBySpeed.add(DogReference.Breed.SPANIEL);
        breedsBySpeed.add(DogReference.Breed.POODLE);
        breedsBySpeed.add(DogReference.Breed.LABRADOR);

        Iterator<DogReference.Breed> itr = breedsBySpeed.iterator();
        while (itr.hasNext()) {
            DogReference.Breed breed = itr.next();
            if (!waitingBreeds.contains(breed)) {
                itr.remove();
            }
        }
        return breedsBySpeed.indexOf(dog.getBreed());
    }

    // Testing - run with "java dogs/g1/Player.java" in src folder
    public static void main(String[] args) {
        Random random = new Random();
        SimPrinter simPrinter = new SimPrinter(true);
        Player player = new Player(1, 1, 1, 1, random, simPrinter);
        double fromEdges = 10.0;

        // TEST 1 - optimal line
        double dist = 2*Math.sqrt(2);
        int n = 2;
        List<ParkLocation> optimalShape = player.getOptimalLocationShape(n, dist, fromEdges);
        simPrinter.println(optimalShape);

        // TEST 2 - optimal equilateral triangle
        double radian = Math.toRadians(-15);
        dist = Math.cos(radian)*5;
        n = 3;
        optimalShape = player.getOptimalLocationShape(n, dist, fromEdges);
        simPrinter.println(optimalShape);

        // TEST 3 - optimal square
        dist = 2;
        n = 4;
        optimalShape = player.getOptimalLocationShape(n, dist, fromEdges);
        simPrinter.println(optimalShape);

        // TEST 4 - optimal regular pentagon
        dist = 3;
        n = 5;
        optimalShape = player.getOptimalLocationShape(n, dist, fromEdges);
        simPrinter.println(optimalShape);

        // TEST 5 - optimal regular hexagon
        dist = 5;
        n = 6;
        optimalShape = player.getOptimalLocationShape(n, dist, fromEdges);
        simPrinter.println(optimalShape);

        // TEST 6 - optimal regular octagon
        dist = Math.sqrt(10);
        n = 8;
        optimalShape = player.getOptimalLocationShape(n, dist, fromEdges);
        simPrinter.println(optimalShape);
    }
}