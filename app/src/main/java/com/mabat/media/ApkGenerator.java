package com.mabat.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.*;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.asn1.x500.X500Name;

public final class ApkGenerator {
    private static final String TEMPLATE = "tikdos-template.apk";
    private static final String HOME_ASSET = "assets/tikdos_home.jpg";
    private static final String ICON_PATH = "res/drawable/logo_tikdos.png";


    public static File create(Context context, InputStream image, File out) throws Exception {
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
        File dir = new File(context.getCacheDir(), "generated");
        if (!dir.exists()) dir.mkdirs();
        File home = new File(dir, "home.jpg");
        File icon = new File(dir, "icon.png");
        saveImages(image, home, icon);

        File unsigned = new File(dir, "unsigned.apk");
        patchTemplate(context, home, icon, unsigned);
        signV1(unsigned, out);
        unsigned.delete();
        home.delete();
        icon.delete();
        return out;
    }

    private static void saveImages(InputStream input, File home, File icon) throws Exception {
        Bitmap src = BitmapFactory.decodeStream(input);
        if (src == null) throw new IOException("לא ניתן לקרוא את התמונה");
        try (FileOutputStream f = new FileOutputStream(home)) {
            src.compress(Bitmap.CompressFormat.JPEG, 92, f);
        }
        int s = Math.min(src.getWidth(), src.getHeight());
        int left = (src.getWidth()-s)/2, top=(src.getHeight()-s)/2;
        Bitmap square = Bitmap.createBitmap(src,left,top,s,s);
        Bitmap scaled = Bitmap.createScaledBitmap(square,1024,1024,true);
        try (FileOutputStream f = new FileOutputStream(icon)) {
            scaled.compress(Bitmap.CompressFormat.PNG,100,f);
        }
        if (square != src) square.recycle();
        scaled.recycle();
        src.recycle();
    }

    private static void patchTemplate(Context c, File home, File icon, File out) throws Exception {
        try (InputStream in=c.getAssets().open(TEMPLATE);
             ZipInputStream zin=new ZipInputStream(in);
             ZipOutputStream zout=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            ZipEntry e;
            byte[] buf=new byte[8192];
            while((e=zin.getNextEntry())!=null){
                String n=e.getName();
                if(n.startsWith("META-INF/")) continue;
                ZipEntry ne=new ZipEntry(n);
                zout.putNextEntry(ne);
                if(HOME_ASSET.equals(n)) copy(new FileInputStream(home),zout,buf);
                else if(ICON_PATH.equals(n)) copy(new FileInputStream(icon),zout,buf);
                else copy(zin,zout,buf);
                zout.closeEntry();
            }
        }
    }

    private static void signV1(File unsigned, File signed) throws Exception {
        LinkedHashMap<String,byte[]> entries=new LinkedHashMap<>();
        try(ZipInputStream zin=new ZipInputStream(new BufferedInputStream(new FileInputStream(unsigned)))){
            ZipEntry e; byte[] buf=new byte[8192];
            while((e=zin.getNextEntry())!=null){
                if(e.getName().startsWith("META-INF/")) continue;
                ByteArrayOutputStream b=new ByteArrayOutputStream();
                int n; while((n=zin.read(buf))>0)b.write(buf,0,n);
                entries.put(e.getName(),b.toByteArray());
            }
        }

        MessageDigest md=MessageDigest.getInstance("SHA-256");
        StringBuilder mf=new StringBuilder("Manifest-Version: 1.0\r\nCreated-By: YB Apps\r\n\r\n");
        ArrayList<byte[]> sections=new ArrayList<>();
        for(Map.Entry<String,byte[]> x:entries.entrySet()){
            String section="Name: "+x.getKey()+"\r\nSHA-256-Digest: "+Base64.getEncoder().encodeToString(md.digest(x.getValue()))+"\r\n\r\n";
            byte[] sb=section.getBytes("UTF-8");
            sections.add(sb);
            mf.append(section);
        }
        byte[] mfBytes=mf.toString().getBytes("UTF-8");
        StringBuilder sf=new StringBuilder("Signature-Version: 1.0\r\nCreated-By: YB Apps\r\nSHA-256-Digest-Manifest: "+Base64.getEncoder().encodeToString(md.digest(mfBytes))+"\r\n\r\n");
        for(byte[] sb:sections){
            String nameLine=new String(sb,"UTF-8");
            String name=nameLine.substring(nameLine.indexOf("Name: ")+6,nameLine.indexOf("\r\n"));
            sf.append("Name: ").append(name).append("\r\nSHA-256-Digest: ").append(Base64.getEncoder().encodeToString(md.digest(sb))).append("\r\n\r\n");
        }
        byte[] sfBytes=sf.toString().getBytes("UTF-8");
        byte[] rsa=makePkcs7(sfBytes);

        try(ZipOutputStream zout=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(signed)))){
            for(Map.Entry<String,byte[]> x:entries.entrySet()){zout.putNextEntry(new ZipEntry(x.getKey()));zout.write(x.getValue());zout.closeEntry();}
            put(zout,"META-INF/MANIFEST.MF",mfBytes);
            put(zout,"META-INF/CERT.SF",sfBytes);
            put(zout,"META-INF/CERT.RSA",rsa);
        }
    }

    private static void put(ZipOutputStream z,String n,byte[] b)throws Exception{z.putNextEntry(new ZipEntry(n));z.write(b);z.closeEntry();}

    private static byte[] makePkcs7(byte[] sfBytes) throws Exception {
        KeyPairGenerator kpg=KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp=kpg.generateKeyPair();

        long now=System.currentTimeMillis();
        X500Name name=new X500Name("CN=Tik Dos Generator,O=YB Apps,C=IL");
        JcaX509v3CertificateBuilder cb=new JcaX509v3CertificateBuilder(
                name,
                BigInteger.valueOf(now).abs(),
                new Date(now-60000L),
                new Date(now+10L*365*24*60*60*1000),
                name,
                kp.getPublic());

        ContentSigner certSigner=new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate());
        X509CertificateHolder holder=cb.build(certSigner);

        ContentSigner signer=new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate());
        CMSSignedDataGenerator gen=new CMSSignedDataGenerator();
        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                new org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder().build())
                .build(signer,holder));
        gen.addCertificate(holder);
        CMSSignedData data=gen.generate(new CMSProcessableByteArray(sfBytes),false);
        return data.getEncoded();
    }

    private static void copy(InputStream in,OutputStream out,byte[] buf)throws IOException{
        int n;
        while((n=in.read(buf))>0) out.write(buf,0,n);
    }
    private static void copy(FileInputStream in,OutputStream out,byte[] buf)throws IOException{
        try(FileInputStream x=in){ copy((InputStream)x,out,buf); }
    }
}