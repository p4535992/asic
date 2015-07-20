package no.difi.asic;

import com.sun.xml.bind.api.JAXBRIContext;
import org.apache.commons.codec.digest.DigestUtils;
import org.etsi.uri._01903.v1_3.*;
import org.etsi.uri._2918.v1_2.ObjectFactory;
import org.etsi.uri._2918.v1_2.XAdESSignaturesType;
import org.w3._2000._09.xmldsig_.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.io.ByteArrayOutputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.GregorianCalendar;

class AsicXadesManifest extends AsicAbstractManifest {

    private static ObjectFactory objectFactory = new ObjectFactory();
    private static JAXBContext jaxbContext; // Thread safe

    static {
        try {
            jaxbContext = JAXBRIContext.newInstance(XAdESSignaturesType.class, X509DataType.class, QualifyingPropertiesType.class);
        } catch (JAXBException e) {
            throw new IllegalStateException(String.format("Unable to create JAXBContext: %s ", e.getMessage()), e);
        }
    }

    // \XAdESSignature\Signature\SignedInfo
    private SignedInfoType signedInfo;
    // \XAdESSignature\Signature\Object\QualifyingProperties\SignedProperties\SignedDataObjectProperties
    private SignedDataObjectPropertiesType signedDataObjectProperties = new SignedDataObjectPropertiesType();

    public AsicXadesManifest(MessageDigestAlgorithm messageDigestAlgorithm) {
        super(messageDigestAlgorithm);

        // \XAdESSignature\Signature\SignedInfo
        signedInfo = new SignedInfoType();

        // \XAdESSignature\Signature\SignedInfo\CanonicalizationMethod
        CanonicalizationMethodType canonicalizationMethod = new CanonicalizationMethodType();
        canonicalizationMethod.setAlgorithm("http://www.w3.org/2006/12/xml-c14n11");
        signedInfo.setCanonicalizationMethod(canonicalizationMethod);

        // \XAdESSignature\Signature\SignedInfo\SignatureMethod
        SignatureMethodType signatureMethod = new SignatureMethodType();
        signatureMethod.setAlgorithm(messageDigestAlgorithm.getUri());
        signedInfo.setSignatureMethod(signatureMethod);
    }

    @Override
    public void add(String filename, String mimeType) {
        String id = String.format("ID_%s", signedInfo.getReference().size());

        {
            // \XAdESSignature\Signature\SignedInfo\Reference
            ReferenceType reference = new ReferenceType();
            reference.setId(id);
            reference.setURI(filename);
            reference.setDigestValue(messageDigest.digest());

            // \XAdESSignature\Signature\SignedInfo\Reference\DigestMethod
            DigestMethodType digestMethodType = new DigestMethodType();
            digestMethodType.setAlgorithm(messageDigestAlgorithm.getUri());
            reference.setDigestMethod(digestMethodType);

            signedInfo.getReference().add(reference);
        }

        {
            // \XAdESSignature\Signature\Object\QualifyingProperties\SignedProperties\SignedDataObjectProperties\DataObjectFormat
            DataObjectFormatType dataObjectFormatType = new DataObjectFormatType();
            dataObjectFormatType.setObjectReference(String.format("#%s", id));
            dataObjectFormatType.setMimeType(mimeType);

            signedDataObjectProperties.getDataObjectFormat().add(dataObjectFormatType);
        }
    }

    public byte[] toBytes(SignatureHelper signatureHelper) {
        // \XAdESSignature
        XAdESSignaturesType xAdESSignaturesType = new XAdESSignaturesType();

        // \XAdESSignature\Signature
        SignatureType signatureType = new SignatureType();
        signatureType.setId("Signature");
        signatureType.setSignedInfo(signedInfo);
        xAdESSignaturesType.getSignature().add(signatureType);

        // \XAdESSignature\Signature\KeyInfo
        KeyInfoType keyInfoType = new KeyInfoType();
        keyInfoType.getContent().add(getX509Data(signatureHelper));
        signatureType.setKeyInfo(keyInfoType);

        // \XAdESSignature\Signature\Object
        ObjectType objectType = new ObjectType();
        objectType.getContent().add(getQualifyingProperties(signatureHelper));
        signatureType.getObject().add(objectType);

        // \XAdESSignature\Signature\Object\SignatureValue
        signatureType.setSignatureValue(getSignature());

        try {
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

            JAXBElement<XAdESSignaturesType> jaxbRootElement = objectFactory.createXAdESSignatures(xAdESSignaturesType);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            marshaller.marshal(jaxbRootElement, baos);
            return baos.toByteArray();
        } catch (JAXBException e) {
            throw new IllegalStateException("Unable to marshall the XAdESSignature into string output", e);
        }

    }

    private JAXBElement<X509DataType> getX509Data(SignatureHelper signatureHelper) {
        org.w3._2000._09.xmldsig_.ObjectFactory objectFactory = new org.w3._2000._09.xmldsig_.ObjectFactory();

        // \XAdESSignature\Signature\KeyInfo\X509Data
        X509DataType x509DataType = new X509DataType();

        for (Certificate certificate : signatureHelper.getCertificateChain()) {
            try {
                // \XAdESSignature\Signature\KeyInfo\X509Data\X509Certificate
                x509DataType.getX509IssuerSerialOrX509SKIOrX509SubjectName().add(objectFactory.createX509DataTypeX509Certificate(certificate.getEncoded()));
            } catch (CertificateEncodingException e) {
                throw new IllegalStateException("Unable to insert certificate.");
            }
        }

        return objectFactory.createX509Data(x509DataType);
    }

    private JAXBElement<QualifyingPropertiesType> getQualifyingProperties(SignatureHelper signatureHelper) {
        // \XAdESSignature\Signature\Object\QualifyingProperties\SignedProperties\SignedSignatureProperties
        SignedSignaturePropertiesType signedSignaturePropertiesType = new SignedSignaturePropertiesType();
        try {
            // \XAdESSignature\Signature\Object\QualifyingProperties\SignedProperties\SignedSignatureProperties\SigningTime
            signedSignaturePropertiesType.setSigningTime(DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar()));
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException("Unable to use current DatatypeFactory", e);
        }

        // \XAdESSignature\Signature\Object\QualifyingProperties\SignedProperties\SignedSignatureProperties\SigningCertificate
        CertIDListType signingCertificate = new CertIDListType();
        signedSignaturePropertiesType.setSigningCertificate(signingCertificate);

        // \XAdESSignature\Signature\Object\QualifyingProperties\SignedProperties\SignedSignatureProperties\SigningCertificate\Cert
        CertIDType cert = new CertIDType();
        signingCertificate.getCert().add(cert);

        try {
            // \XAdESSignature\Signature\Object\QualifyingProperties\SignedProperties\SignedSignatureProperties\SigningCertificate\Cert\CertDigest
            DigestAlgAndValueType certDigest = new DigestAlgAndValueType();
            certDigest.setDigestValue(DigestUtils.sha1(signatureHelper.getX509Certificate().getEncoded()));
            cert.setCertDigest(certDigest);

            // \XAdESSignature\Signature\Object\QualifyingProperties\SignedProperties\SignedSignatureProperties\SigningCertificate\Cert\CertDigest\DigestMethod
            DigestMethodType digestMethodType = new DigestMethodType();
            digestMethodType.setAlgorithm("http://www.w3.org/2000/09/xmldsig#sha1");
            certDigest.setDigestMethod(digestMethodType);
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException("Unable to encode certificate.", e);
        }

        // \XAdESSignature\Signature\Object\QualifyingProperties\SignedProperties\SignedSignatureProperties\SigningCertificate\Cert\IssuerSerial
        X509IssuerSerialType issuerSerialType = new X509IssuerSerialType();
        issuerSerialType.setX509IssuerName(signatureHelper.getX509Certificate().getIssuerX500Principal().getName());
        issuerSerialType.setX509SerialNumber(signatureHelper.getX509Certificate().getSerialNumber());
        cert.setIssuerSerial(issuerSerialType);

        // \XAdESSignature\Signature\Object\QualifyingProperties\SignedProperties
        SignedPropertiesType signedPropertiesType = new SignedPropertiesType();
        signedPropertiesType.setId("SignedProperties");
        signedPropertiesType.setSignedSignatureProperties(signedSignaturePropertiesType);
        signedPropertiesType.setSignedDataObjectProperties(signedDataObjectProperties);

        // \XAdESSignature\Signature\Object\QualifyingProperties
        QualifyingPropertiesType qualifyingPropertiesType = new QualifyingPropertiesType();
        qualifyingPropertiesType.setSignedProperties(signedPropertiesType);
        qualifyingPropertiesType.setTarget("#Signature");

        // Adding digest of SignedProperties into SignedInfo
        {
            // \XAdESSignature\Signature\SignedInfo\Reference
            ReferenceType reference = new ReferenceType();
            reference.setType("http://uri.etsi.org/01903#SignedProperties");
            reference.setURI("#SignedProperties");
            // TODO Generate digest

            // \XAdESSignature\Signature\SignedInfo\Reference\Transforms
            TransformsType transformsType = new TransformsType();
            reference.setTransforms(transformsType);

            // \XAdESSignature\Signature\SignedInfo\Reference\Transforms\Transform
            TransformType transformType = new TransformType();
            transformType.setAlgorithm("http://www.w3.org/TR/2001/REC-xml-c14n-20010315");
            reference.getTransforms().getTransform().add(transformType);

            // \XAdESSignature\Signature\SignedInfo\Reference\DigestMethod
            DigestMethodType digestMethodType = new DigestMethodType();
            digestMethodType.setAlgorithm(messageDigestAlgorithm.getUri());
            reference.setDigestMethod(digestMethodType);

            signedInfo.getReference().add(reference);
        }

        return new org.etsi.uri._01903.v1_3.ObjectFactory().createQualifyingProperties(qualifyingPropertiesType);
    }

    protected SignatureValueType getSignature() {
        // TODO Generate signature
        // http://stackoverflow.com/questions/30596933/xades-bes-detached-signedproperties-reference-wrong-digestvalue-java

        /*
        DigestMethod dm = fac.newDigestMethod(DigestMethod.SHA1, null);
        CanonicalizationMethod cn = fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS,(C14NMethodParameterSpec) null);

        List<Reference> refs = new ArrayList<Reference>();
        Reference ref1 = fac.newReference(pathName, dm,null,null,signedRefID,messageDigest2.digest(datax));
        refs.add(ref1);

        Canonicalizer cn14 = Canonicalizer.getInstance(Canonicalizer.ALGO_ID_C14N11_OMIT_COMMENTS);
        byte[] canon;
        canon = cn14.canonicalizeSubtree(SPElement);
        Reference ref2 = fac.newReference("#"+signedPropID,dm, null , sigProp , signedPropRefID,messageDigest2.digest(canon));
        refs.add(ref2);

        SignatureMethod sm = fac.newSignatureMethod(SignatureMethod.RSA_SHA1, null);
        SignedInfo si = fac.newSignedInfo(cn, sm, refs);

        XMLSignature signature = fac.newXMLSignature(si, ki,objects,signatureID,null);

        signature.sign(dsc);
        */

        SignatureValueType signatureValue = new SignatureValueType();
        return signatureValue;
    }
}
