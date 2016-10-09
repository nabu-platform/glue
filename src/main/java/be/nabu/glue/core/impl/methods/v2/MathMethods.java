package be.nabu.glue.core.impl.methods.v2;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.GlueUtils.ObjectHandler;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.evaluator.impl.ClassicOperation;

@MethodProviderClass(namespace = "math")
public class MathMethods {
	
	private static final BigDecimal PI = new BigDecimal("3.14159265358979323846264338327950288419716939937510582097494459230781640628620899862803482534211706798214808651328230664709384460955058223172535940812848111745028410270193852110555964462294895493038196442881097566593344612847564823378678316527120190914564856692346034861045432664821339360726024914127372458700660631558817488152092096282925409171536436789259036001133053054882046652138414695194151160943305727036575959195309218611738193261179310511854807446237996274956735188575272489122793818301194912983367336244065664308602139494639522473719070217986094370277053921717629317675238467481846766940513200056812714526356082778577134275778960917363717872146844090122495343014654958537105079227968925892354201995611212902196086403441815981362977477130996051870721134999999837297804995105973173281609631859502445945534690830264252230825334468503526193118817101000313783875288658753320838142061717766914730359825349042875546873115956286388235378759375195778185778053217122680661300192787661119590921642019893809525720106548586327886593615338182796823030195203530185296899577362259941389124972177528347913151557485724245415069595082953311686172785588907509838175463746493931925506040092770167113900984882401285836160356370766010471018194295559619894676783744944825537977472684710404753464620804668425906949129331367702898915210475216205696602405803815019351125338243003558764024749647326391419927260426992279678235478163600934172164121992458631503028618297455570674983850549458858692699569092721079750930295532116534498720275596023648066549911988183479775356636980742654252786255181841757467289097777279380008164706001614524919217321721477235014144197356854816136115735255213347574184946843852332390739414333454776241686251898356948556209921922218427255025425688767179049460165346680498862723279178608578438382796797668145410095388378636095068006422512520511739298489608412848862694560424196528502221066118630674427862203919494504712371378696095636437191728746776465757396241389086583264599581339047802759009946576407895126946839835259570982582262052248940772671947826848260147699090264013639443745530506820349625245174939965143142980919065925093722169646151570985838741059788595977297549893016175392846813826868386894277415599185592524595395943104997252468084598727364469584865383673622262609912460805124388439045124413654976278079771569143599770012961608944169486855584840635342207222582848864815845602850601684273945226746767889525213852254995466672782398645659611635488623057745649803559363456817432411251507606947945109659609402522887971089314566913686722874894056010150330861792868092087476091782493858900971490967598526136554978189312978482168299894872265880485756401427047755513237964145152374623436454285844479526586782105114135473573952311342716610213596953623144295248493718711014576540359027993440374200731057853906219838744780847848968332144571386875194350643021845319104848100537061468067491927819119793995206141966342875444064374512371819217999839101591956181467514269123974894090718649423196156794520809514655022523160388193014209376213785595663893778708303906979207734672218256259966150142150306803844773454920260541466592");
	private static final BigDecimal E = new BigDecimal("2.7182818284590452353602874713526624977572470936999595749669676277240766303535475945713821785251664274274663919320030599218174135966290435729003342952605956307273100853237805275106368648701695314186552748459082449550453392864976427741366416596463663250873609158413439709998317035382338009211681465541537493054202224617093212309491677634993111307030292569893420676439191366503848735788466107757255763079218988673537904194120433774064949070738630790492489764370698362973668621984292507677002141574065002938269544068718779542709697662474652436662951385720192083031772692340977016567453922577791473416036849357231033044857614290266332635293797344504000613119416470868982597552087347829370853870094341780806567997280704595039170133514312438730052201840596596290585721481240842118500647750398179419612185733693597332336227260602518178388927025136194920607824386937023374814484201715707221499854656151809995508987059685112005970217969141325866928660231731022979729068783220835224413915990618593145821470347881544516647983250462625226802944497473484653275180616483206218085347503591398004482219928754115421760307308298093805920594877077289150276094679343039089600258059624590109090386356736454543843794457045921855094655336010469921962626941012691045890340647723383513632617624742197059501772297495397551854979415896674068860108739844370091401280168672659342716355230282166024777190883709481586964520566688159693214109494395036740722097060195710638641256578634625313903205359261398065461279426613181836499453900527608815525900579545890516959001648050702509640314333069713484616443772306827052233389169369334390770049778565096643440523259962569282784051524699249491988237669527398542881055445275659862947524520569335811911533354301970071676986468303063452640299456893255134188798102638228436937834323104554600843767136785855400416373009105123884404102737407214076089146349592070628050009074145432574228970773743592716838514050834569168569402006962806914297770127238038300197427280701490979238638997959936121046054661328781583801060085135257338860578660082268733135926119444879207076024837191753780784440934794194239955794649456747566287015594978328413335919147862146373560315712371597252523508134099288285043454750472969427400999348528521235973917674488103383877169147599028815092782273697356447476531171632071581081270552559885185650280747000443005327106698520241259397263439600130178175428172280699015701997418112250357373092905792348289915876331111795795255780918373793205126924035782459546113178511496863963999147610227071466522780228905568108950901467369301961741300254045171967933853060695118991479404092568793693380703384411796021075734165775088763567555419085563463241234355683082252352737122171055033873426466353002596791143293082642935286817289671697579664184368089350721853584011065482617985869437542874723693228725609077359083959083910914601426141170721844621173694252316085503259541570930318976978992814629932474988228409240148546612084557584170924194304020150691861366278781074126328197437166651633000817076088469589085204805903070927984986199860383851680021154453092698211263222953723523749313973888100541833082146561100109711466");
	
	@GlueMethod(description = "Set and/or view the current math context", version = 2)
	public static MathContext rounding(@GlueParam(name = "scale") Integer scale, @GlueParam(name = "rounding") RoundingMode roundingMode) {
		MathContext current = ClassicOperation.getMathContext();
		if (scale != null || roundingMode != null) {
			current = new MathContext(scale == null ? current.getPrecision() : scale, roundingMode == null ? current.getRoundingMode() : roundingMode);
			ClassicOperation.setMathContext(current);
		}
		return current;
	}
	
	@GlueMethod(description = "Returns the absolute value of the given number", version = 2)
	public static Object abs(Object...original) {
		return GlueUtils.wrap(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				if (single instanceof BigInteger) {
					return ((BigInteger) single).abs();
				}
				else if (single instanceof BigDecimal) {
					return ((BigDecimal) single).abs();
				}
				else if (single instanceof Long) {
					return Math.abs((Long) single);
				}
				else if (single instanceof Integer) {
					return Math.abs((Integer) single);
				}
				return Math.abs(GlueUtils.convert(single, Double.class));
			}
		}, false, original);
	}

	@GlueMethod(description = "Returns the cosine of the given number", version = 2)
	public static Object cos(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.cos((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Returns the hyperbolic cosine of the given number", version = 2)
	public static Object cosh(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.cosh((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Returns the sine of the given number", version = 2)
	public static Object sin(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.sin((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Returns the hyperbolic sine of the given number", version = 2)
	public static Object sinh(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.sinh((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Returns the trigonometric tangent of the given number", version = 2)
	public static Object tan(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.tan((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Returns the hyperbolic tangent of the given number", version = 2)
	public static Object tanh(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.tanh((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Returns the arc cosine of the given number", version = 2)
	public static Object acos(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.acos((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Returns the arc sine of the given number", version = 2)
	public static Object asin(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.asin((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Returns the arc tangent of the given number", version = 2)
	public static Object atan(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.atan((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Returns the angle theta from the conversion of rectangular coordinates (x, y) to polar coordinates (r, theta)", version = 2)
	public static Object atan2(@GlueParam(name = "y") Object y, @GlueParam(name = "x") Object x) {
		return Math.atan2(GlueUtils.convert(y, Double.class), GlueUtils.convert(x, Double.class));
	}
	
	@GlueMethod(description = "Returns the cube root of the given number", version = 2)
	public static Object cbrt(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.cbrt((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Returns the ceil value of the given number", version = 2)
	public static Object ceil(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.ceil((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Returns the floor value of the given number", version = 2)
	public static Object floor(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.floor((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Returns the angle theta from the conversion of rectangular coordinates (x, y) to polar coordinates (r, theta)", version = 2)
	public static Object hypot(@GlueParam(name = "x") Object x, @GlueParam(name = "y") Object y) {
		return Math.hypot(GlueUtils.convert(x, Double.class), GlueUtils.convert(y, Double.class));
	}
	
	@GlueMethod(description = "Returns the natural logarithm (base e) of the given number", version = 2)
	public static Object log(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.log((Double) single);
			}
		}, Double.class), false, original);
	}

	@GlueMethod(description = "Returns the base 10 logarithm of the given number", version = 2)
	public static Object log10(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.log10((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Returns a random number between 0.0 and 1.0", version = 2)
	public static Double random() {
		return Math.random();
	}
	
	@GlueMethod(description = "Returns the square root of the given number", version = 2)
	public static Object sqrt(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.sqrt((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Converts the given radians to degrees", version = 2)
	public static Object degrees(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.toDegrees((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Converts the degrees to radians", version = 2)
	public static Object radians(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Math.toRadians((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Converts the degrees to radians", version = 2)
	public static Object round(final Integer amountOfNumbers, Object...original) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < (amountOfNumbers == null ? 0 : amountOfNumbers); i++) {
			builder.append("#");
		}
		final String format = "#" + (builder.toString().isEmpty() ? "" : "." + builder.toString());
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				DecimalFormat formatter = new DecimalFormat(format);
				return formatter.format((Double) single);
			}
		}, Double.class), false, original);
	}
	
	@GlueMethod(description = "Returns pi", version = 2)
	public static Object pi(@GlueParam(name = "big") Boolean big) {
		return big == null || !big ? Math.PI : PI;
	}
	
	@GlueMethod(description = "Returns e", version = 2)
	public static Object e(@GlueParam(name = "big") Boolean big) {
		return big == null || !big ? Math.E : E;
	}
}
