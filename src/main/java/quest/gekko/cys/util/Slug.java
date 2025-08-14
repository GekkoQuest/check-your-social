package quest.gekko.cys.util;

public class Slug {
    public static String of(String s) {
        return s==null?"":s.trim().toLowerCase().replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)","");
    }
}