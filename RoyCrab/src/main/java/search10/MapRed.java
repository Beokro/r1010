package search10;

public class MapRed {
    Thread map;
    Thread reduce;
    MapRed(Thread map, Thread reduce) {
        this.map = map;
        this.reduce = reduce;
    }
}
