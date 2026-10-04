package com.mabat.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import java.io.*;
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

public final class ApkGenerator {
    private static final String TEMPLATE = "tikdos-template.apk";
    private static final String HOME_ASSET = "assets/tikdos_home.jpg";
    private static final String ICON_PATH = "res/drawable/logo_tikdos.png";

    private static final String KEY_B64 =
        "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC0Kz8xEpm0ZgXjYyMKU8aPJmXBtHWB2p/mh1N99YwLytACm4319AENxrVDpphiOGPZANgeDILnQSnTi5yrh6LqphNX09qV2pVD32KloYa2KUq1B0WBu7dVFSlG3noDdvupgY56agmGNiCZRCxDF1mcf+ch9loh1FvepAz0wy6AT1CcSGKsSov4U6I3efli1/BoyG27ioQQdxVSMHh5mB3H6oVrglN8DyJO08QVrd0aWTSdIZo6WnAy7BzY4AcGlyksCBi5O9gvNE73i4tXca56PmS38rpwYl5fZu8H3bAh+vdU4inryT7Fsi3nVO9zJtFVrbFVr+pZqiUGz4ZD/+NXAgMBAAECggEAQHB3WYXdk7gpwvpiPf9YtTp2iqGQpbEcH8enBRYd0NnpLAWW8LEk71hy3I9pUTf5/lNe+OBgsXWNECyPDIqmqmZb5L3RIGfdmzj6f2MWW3DJV5YSNiR9neDDsqU/3p1fxXChFQd4AJi7cBYB/r0LP/4/HNaPxim98hOazrBAoYMhDY0OP6T/W7Ou21vm1XXMnEcDXBTtVE+tBMGEr8kuityPMm6T3Lg83WRDYbWu9p+ATvytNJYrdpxzw8v4aSE9JED26a/RH1dJOvWGVjbGhkdNZfDjAXFkXe7iBLeWn6bmyO/CYZG6APUqvMT+aHHfAIGq4ZBX6ZYhYq93DN9zAQKBgQD9Gbih0iTt6H20Iutr0zgLVdy5lFffLe8uDzaON/E0tAz3I4ri3LiAx2s40Sx0Wruwxx6sqwhkamyp/sVkUUtu5HVR7iaSlDPVC671R++j7spr2v6+mECtSAWY36aKyEHYjbpRiZsZfyqxLK//z+lUrUkpYfiDYYphulZAx+0a/wKBgQC2O6LYp0LPgbxNU4Wd+Nt1vUGuhwqzHO2XAXaURKVbJCuOd4VwJkiL+VRD6CRDZMPiCh46yGzIBIX5wGtoozEZGwjIEyK3C8H/wDBy+8serCdk4pBILpT1mS32W6j0hI/8Li7i2oi2G+aYUUbNPbX6n0x3faRAascMPtWazLvvqQKBgQCEsk+Sx9KEyTfekdBMH9lqWAH5akUHxdV4mJFJzxqvPxbtG71dY8t3+RXGHCTqfAebj0oNzT4BJ6sBFYn4JoceBLld0zZ18y2ZrR51itmhYTjPg2m8E6cVMoV8uQVgDc4381aUGUkv/PQtH/jGbgMvWgrZn3vjpegfhMpj/G3bkQKBgG1Axbn0VVbI5hbJ+zKhbxlglM9IwP5vWnYM94WevjAtODvaplJ9cUMSARYRSCRrdhC/li4R2T31kEkStpJuPFJb5eDltq+UT4XAOIvHL5i9B4UxW+UaoE26P1cSzvGBxD0hhxSUhiAADDjHiZYk6ERQWuzMk7vXCGzsAu4izo25AoGAYkjbIv4NEQrsZNQ1Vp1NEiJlPanc3SS6oXnJN2fM4X6rftcKQnJM9nUv72u0btwuAomuFL1uECGMqxnGKuDXFstttQeSd22WA523aKyRpAx2v2/dMip1m6GwWpzepMPYeFBRi0Uch9bC0whRxo6JxJqH0oeYx7mmbUKSTpIGdrA=";
    private static final String CERT_B64 =
        "MIIDVzCCAj+gAwIBAgIUCteOeexjg6UubcpHdQbV0/k5XqowDQYJKoZIhvcNAQELBQAwOzEaMBgGA1UEAwwRVGlrIERvcyBHZW5lcmF0b3IxEDAOBgNVBAoMB1lCIEFwcHMxCzAJBgNVBAYTAklMMB4XDTI2MTAwNDIxMDA1M1oXDTM2MTAwMTIxMDA1M1owOzEaMBgGA1UEAwwRVGlrIERvcyBHZW5lcmF0b3IxEDAOBgNVBAoMB1lCIEFwcHMxCzAJBgNVBAYTAklMMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtCs/MRKZtGYF42MjClPGjyZlwbR1gdqf5odTffWMC8rQApuN9fQBDca1Q6aYYjhj2QDYHgyC50Ep04ucq4ei6qYTV9PaldqVQ99ipaGGtilKtQdFgbu3VRUpRt56A3b7qYGOemoJhjYgmUQsQxdZnH/nIfZaIdRb3qQM9MMugE9QnEhirEqL+FOiN3n5YtfwaMhtu4qEEHcVUjB4eZgdx+qFa4JTfA8iTtPEFa3dGlk0nSGaOlpwMuwc2OAHBpcpLAgYuTvYLzRO94uLV3Guej5kt/K6cGJeX2bvB92wIfr3VOIp68k+xbIt51TvcybRVa2xVa/qWaolBs+GQ//jVwIDAQABo1MwUTAdBgNVHQ4EFgQUOkmDCHQW8Dj6e3tYR95Ze0bVBeYwHwYDVR0jBBgwFoAUOkmDCHQW8Dj6e3tYR95Ze0bVBeYwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAqqyZquo8CGm9MiocofyQxIh+/motujwrDJr+RcKJXCzFZEOiz+9VlZwX+qlpKgJdn5TXg6UeLfYs9pG0yJRwKWhtIcOybujsQfBCMIOjJ7Q+laSlSVh4oewtgr5VbJzele2AY1TuEPomU10BdfEJT2VRZPmgM/KqUOP0P/1APTuyB1w+Wz4WP6kLkOGyxC/Nrtzr+DdwVnfyUJw19eVj7WJXqjx1EP6Wi4QIyEgGDwM+ieWfowBnJy8oZB39HU7XDLqs4zlcl9gWFRdqMltO7J8v3nXfvstI1vcovTZGoop0cKJ1fjOmnZEqnY+BlBBnflG5qduDNmBiARIXd/Xaw==";

    public static File create(Context context, InputStream image, File out) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
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
        byte[] key=Base64.getDecoder().decode(KEY_B64);
        byte[] cert=Base64.getDecoder().decode(CERT_B64);
        PrivateKey pk=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key));
        CertificateFactory cf=CertificateFactory.getInstance("X.509");
        Certificate c=cf.generateCertificate(new ByteArrayInputStream(cert));
        X509CertificateHolder holder=new JcaX509CertificateHolder((java.security.cert.X509Certificate)c);
        ContentSigner signer=new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(pk);
        CMSSignedDataGenerator gen=new CMSSignedDataGenerator();
        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                new org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
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