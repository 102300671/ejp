package client.gui.utils;

import java.awt.Font;
import java.awt.GraphicsEnvironment;

public class FontUtils {
    private static String chineseFontName = null;
    
    static {
        findChineseFont();
    }
    
    private static void findChineseFont() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fontNames = ge.getAvailableFontFamilyNames();
        
        String[] preferredFonts = {
            "Noto Sans CJK SC",
            "Noto Sans CJK TC",
            "Noto Sans CJK JP",
            "Noto Sans CJK KR",
            "SimHei",
            "Microsoft YaHei",
            "Microsoft JhengHei",
            "STHeiti",
            "STSong",
            "STKaiti",
            "STFangsong",
            "WenQuanYi Micro Hei",
            "WenQuanYi Zen Hei",
            "Source Han Sans SC",
            "Source Han Sans TC",
            "Source Han Sans JP",
            "Source Han Sans KR",
            "PingFang SC",
            "PingFang TC",
            "PingFang HK",
            "Hiragino Sans GB",
            "Yu Gothic",
            "Yu Mincho",
            "Meiryo",
            "Malgun Gothic",
            "Apple SD Gothic Neo"
        };
        
        for (String fontName : preferredFonts) {
            for (String availableFont : fontNames) {
                if (availableFont.equalsIgnoreCase(fontName) || availableFont.contains(fontName)) {
                    chineseFontName = availableFont;
                    return;
                }
            }
        }
        
        chineseFontName = Font.SANS_SERIF;
    }
    
    public static Font getChineseFont(int style, int size) {
        return new Font(chineseFontName, style, size);
    }
    
    public static Font getChineseFont() {
        return getChineseFont(Font.PLAIN, 14);
    }
    
    public static Font getChineseFont(int size) {
        return getChineseFont(Font.PLAIN, size);
    }
    
    public static String getChineseFontName() {
        return chineseFontName;
    }
}