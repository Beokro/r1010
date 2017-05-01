package search10;

public class RoundFactory {
    public static MapRed makeRound(int round) {
        switch(round) {
            case 1:
                return new MapRed(new Round1Map(), new Round1Red());
            case 2:
                return new MapRed(new Round2Map(), new Round2Red());
            case 3:
                return new MapRed(new Round3Map(), new Round3Red());
            case 4:
                return new MapRed(new Round4Map(), new Round4Red());
            case 5:
                return new MapRed(new Round5Map(), new Round5Red());
            default:
                System.err.println("Invalid Round");
        }
        return null;
    }
}
