package net.teamfruit.eewbot.entity.jma;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import net.teamfruit.eewbot.entity.jma.telegram.*;
import reactor.util.annotation.Nullable;

import java.util.Optional;

public enum JMAXmlType {
    VGSK50("季節観測"),
    VGSK55("生物季節観測"),
    VGSK60("特殊気象報"),
    VPTI50("全般台風情報"),
    VPTI51("全般台風情報（定型）"),
    VPTI52("全般台風情報（詳細）"),
    VPTW40("台風解析・予報情報（３日予報）"),
    VPTW41("台風解析・予報情報（３日予報）"),
    VPTW42("台風解析・予報情報（３日予報）"),
    VPTW43("台風解析・予報情報（３日予報）"),
    VPTW44("台風解析・予報情報（３日予報）"),
    VPTW45("台風解析・予報情報（３日予報）"),
    VPTW50("台風解析・予報情報（５日予報）"),
    VPTW51("台風解析・予報情報（５日予報）"),
    VPTW52("台風解析・予報情報（５日予報）"),
    VPTW53("台風解析・予報情報（５日予報）"),
    VPTW54("台風解析・予報情報（５日予報）"),
    VPTW55("台風解析・予報情報（５日予報）"),
    VPTW60("台風解析・予報情報（５日予報）（Ｈ３０）"),
    VPTW61("台風解析・予報情報（５日予報）（Ｈ３０）"),
    VPTW62("台風解析・予報情報（５日予報）（Ｈ３０）"),
    VPTW63("台風解析・予報情報（５日予報）（Ｈ３０）"),
    VPTW64("台風解析・予報情報（５日予報）（Ｈ３０）"),
    VPTW65("台風解析・予報情報（５日予報）（Ｈ３０）"),
    VPZU50("全般海上警報（定時）"),
    VPZU52("全般海上警報（定時）（Ｈ２９）"),
    VPZU51("全般海上警報（臨時）"),
    VPZU53("全般海上警報（臨時）（Ｈ２９）"),
    VPCU50("地方海上警報"),
    VPCU51("地方海上警報（Ｈ２８）"),
    VPCY50("地方海上予報"),
    VPCY51("地方海上予報（Ｈ２８）"),
    VPWW50("気象警報・注意報"),
    VPWW53("気象特別警報・警報・注意報"),
    VPWW54("気象警報・注意報（Ｈ２７）"),
    VXKO50("指定河川洪水予報"),
    VXKO51("指定河川洪水予報"),
    VXKO52("指定河川洪水予報"),
    VXKO53("指定河川洪水予報"),
    VXKO54("指定河川洪水予報"),
    VXKO55("指定河川洪水予報"),
    VXKO56("指定河川洪水予報"),
    VXKO57("指定河川洪水予報"),
    VXKO58("指定河川洪水予報"),
    VXKO59("指定河川洪水予報"),
    VXKO60("指定河川洪水予報"),
    VXKO61("指定河川洪水予報"),
    VXKO62("指定河川洪水予報"),
    VXKO63("指定河川洪水予報"),
    VXKO64("指定河川洪水予報"),
    VXKO65("指定河川洪水予報"),
    VXKO66("指定河川洪水予報"),
    VXKO67("指定河川洪水予報"),
    VXKO68("指定河川洪水予報"),
    VXKO69("指定河川洪水予報"),
    VXKO70("指定河川洪水予報"),
    VXKO71("指定河川洪水予報"),
    VXKO72("指定河川洪水予報"),
    VXKO73("指定河川洪水予報"),
    VXKO74("指定河川洪水予報"),
    VXKO75("指定河川洪水予報"),
    VXKO76("指定河川洪水予報"),
    VXKO77("指定河川洪水予報"),
    VXKO78("指定河川洪水予報"),
    VXKO79("指定河川洪水予報"),
    VXKO80("指定河川洪水予報"),
    VXKO81("指定河川洪水予報"),
    VXKO82("指定河川洪水予報"),
    VXKO83("指定河川洪水予報"),
    VXKO84("指定河川洪水予報"),
    VXKO85("指定河川洪水予報"),
    VXKO86("指定河川洪水予報"),
    VXKO87("指定河川洪水予報"),
    VXKO88("指定河川洪水予報"),
    VXKO89("指定河川洪水予報"),
    VXWW50("土砂災害警戒情報"),
    VPOA50("記録的短時間大雨情報"),
    VPHW50("竜巻注意情報"),
    VPHW51("竜巻注意情報（目撃情報付き）"),
    VPZJ50("全般気象情報"),
    VPCJ50("地方気象情報"),
    VPFJ50("府県気象情報"),
    VPFG50("府県天気概況"),
    VPFD50("府県天気予報"),
    VPFD51("府県天気予報（Ｒ１）"),
    VPFW50("府県週間天気予報"),
    VPSG50("スモッグ気象情報"),
    VPZI50("全般天候情報"),
    VPCI50("地方天候情報"),
    VXSE51("震度速報", VXSE51.class),
    VXSE52("震源に関する情報", VXSE52.class),
    VXSE61("顕著な地震の震源要素更新のお知らせ", VXSE61.class),
    VXSE60("地震回数に関する情報"),
    VXSE56("地震の活動状況等に関する情報"),
    VXSE53("震源・震度に関する情報", VXSE53.class),
    VXSE44("緊急地震速報（予報）"),
    VXSE43("緊急地震速報（警報）"),
    VTSE51("津波情報a"),
    VTSE41("津波警報・注意報・予報a"),
    VZSE40("地震・津波に関するお知らせ"),
    VZVO40("火山に関するお知らせ"),
    VFVO52("噴火に関する火山観測報"),
    VFVO51("火山の状況に関する解説情報"),
    VFVO50("噴火警報・予報"),
    VFSV50("火山現象に関する海上警報・海上予報"),
    VFSV51("火山現象に関する海上警報・海上予報"),
    VFSV52("火山現象に関する海上警報・海上予報"),
    VFSV53("火山現象に関する海上警報・海上予報"),
    VFSV54("火山現象に関する海上警報・海上予報"),
    VFSV55("火山現象に関する海上警報・海上予報"),
    VFSV56("火山現象に関する海上警報・海上予報"),
    VFSV57("火山現象に関する海上警報・海上予報"),
    VFSV58("火山現象に関する海上警報・海上予報"),
    VFSV59("火山現象に関する海上警報・海上予報"),
    VFSV60("火山現象に関する海上警報・海上予報"),
    VFSV61("火山現象に関する海上警報・海上予報"),
    VMCJ50("全般潮位情報"),
    VMCJ51("地方潮位情報"),
    VMCJ52("府県潮位情報"),
    VPZK50("全般１か月予報，全般３か月予報，全般暖・寒候期予報"),
    VPCK50("地方１か月予報，地方３か月予報，地方暖・寒候期予報"),
    VXSE42("緊急地震速報配信テスト"),
    VPZS50("全般スモッグ気象情報"),
    VPFT50("熱中症警戒アラート"),
    VZSA50("地上実況図"),
    VZSF50("地上２４時間予想図"),
    VZSF51("地上４８時間予想図"),
    VTSE52("沖合の津波観測に関する情報"),
    VPNO50("気象特別警報報知"),
    VZSA60("アジア太平洋地上実況図"),
    VZSF60("アジア太平洋海上悪天２４時間予想図"),
    VZSF61("アジア太平洋海上悪天４８時間予想図"),
    VFVO53("降灰予報（定時）"),
    VFVO54("降灰予報（速報）"),
    VFVO55("降灰予報（詳細）"),
    VFVO56("噴火速報"),
    VPFD60("警報級の可能性（明日まで）"),
    VPFW60("警報級の可能性（明後日以降）"),
    VPZK70("全般季節予報（2週間気温予報）"),
    VPCK70("地方季節予報（2週間気温予報）"),
    VPAW51("早期天候情報"),
    VPRN50("大雨危険度通知"),
    VYSE50("南海トラフ地震臨時情報"),
    VYSE51("南海トラフ地震関連解説情報"),
    VYSE52("南海トラフ地震関連解説情報"),
    VPTA50("台風の暴風域に入る確率"),
    VPTA51("台風の暴風域に入る確率"),
    VPTA52("台風の暴風域に入る確率"),
    VPTA53("台風の暴風域に入る確率"),
    VPTA54("台風の暴風域に入る確率"),
    VPTA55("台風の暴風域に入る確率"),
    VXSE45("緊急地震速報（地震動予報）"),
    VXSE62("長周期地震動に関する観測情報", VXSE62.class),
    VFVO60("推定噴煙流向報");

    private final String title;
    private final Class<? extends JMAReport> reportClass;

    JMAXmlType(String title) {
        this.title = title;
        this.reportClass = null;
    }

    JMAXmlType(String title, Class<? extends JMAReport> clazz) {
        this.title = title;
        this.reportClass = clazz;
    }

    @JsonValue
    public String getTitle() {
        return this.title;
    }

    public Optional<Class<? extends JMAReport>> getReportClass() {
        return Optional.ofNullable(this.reportClass);
    }

    @JsonCreator
    public static @Nullable JMAXmlType from(String title) {
        for (JMAXmlType value : values()) {
            if (value.title.equals(title))
                return value;
        }
        return null;
    }
}
