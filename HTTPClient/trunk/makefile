
### general JDK definitions

JC	 = $(JAVA_HOME)/bin/javac
JDOC	 = $(JAVA_HOME)/bin/javadoc
JFLAGS   = -g -O
JDFLAGS  = -J-Xmx35m -J-Xms20m
CLASSP   = -classpath "$(JAVA_HOME)/lib/classes.zip:..:$(HOME)/www/java/classes"
JDCLASSP = -classpath "$(JAVA_HOME)/lib/classes.zip:.."

JTAGS    = $(HOME)/bin/jtags
JTFLAGS  = -f 'http https shttp'

RM       = /usr/bin/rm -f
MV       = /usr/bin/mv -f


### java extensions

.SUFFIXES: .java .class

.java.class:
	$(JC) $(JFLAGS) $(CLASSP) $<


### all source files

classes = HTTPConnection.class \
	  HTTPResponse.class \
	  HttpOutputStream.class \
	  HttpOutputStreamFilter.class \
	  NVPair.class \
	  MD4.class \
	  MD5.class \
	  ResponseHandler.class \
	  RespInputStream.class \
	  StreamDemultiplexor.class \
	  ExtBufferedInputStream.class \
	  ExtByteArrayOutputStream.class \
	  IdempotentSequence.class \
	  SocksClient.class \
	  RoRequest.class \
	  RoResponse.class \
	  Request.class \
	  Response.class \
	  CIHashtable.class \
	  HTTPClientModule.class \
	  HTTPClientModuleAdapter.class \
	  HTTPClientModuleConstants.class \
	  RedirectionModule.class \
	  RetryModule.class \
	  RetryException.class \
	  AuthorizationModule.class \
	  AuthorizationInfo.class \
	  AuthorizationHandler.class \
	  DefaultAuthHandler.class \
	  AuthorizationPrompter.class \
	  CookieModule.class \
	  Cookie.class \
	  Cookie2.class \
	  CookiePolicyHandler.class \
	  ContentMD5Module.class \
	  MD5InputStream.class \
	  HashVerifier.class \
	  DefaultModule.class \
	  TransferEncodingModule.class \
	  ContentEncodingModule.class \
	  ChunkedInputStream.class \
	  UncompressInputStream.class \
	  RetryAfterModule.class \
	  JunkbusterModule.class \
	  HttpURLConnection.class \
	  http/Handler.class \
	  https/Handler.class \
	  shttp/Handler.class \
	  GlobalConstants.class \
	  URI.class \
	  Util.class \
	  HttpHeaderElement.class \
	  FilenameMangler.class \
	  Codecs.class \
	  LinkedList.class \
	  ModuleException.class \
	  ProtocolNotSuppException.class \
	  AuthSchemeNotImplException.class \
	  ParseException.class \
	  SocksException.class


### targets 

all: $(classes)

doc::
	- $(RM) `ls doc/api/*.html | grep -v help.html`
	$(JDOC) $(JDFLAGS) $(JDCLASSP) -author -version -public -d doc/api \
	    HTTPClient HTTPClient.http HTTPClient.https
	cd doc/api; fixup_links

full-doc::
	- $(RM) `ls full_api/*.html | grep -v API_users_guide`
	$(JDOC) $(JDFLAGS) $(JDCLASSP) -author -version -private -d doc/full_api \
	    HTTPClient HTTPClient.http HTTPClient.https
	cd doc/full_api; fixup_links

tags::
	$(JTAGS) $(JTFLAGS) . > /dev/null 2>&1

kit::
	- $(RM) HTTPClient.zip
	- $(RM) HTTPClient.tar.gz
	cd ../; zip -r9 HTTPClient.zip HTTPClient ie
	cd ../; tar hcf HTTPClient.tar HTTPClient ie
	cd ../; $(MV) HTTPClient.zip HTTPClient
	cd ../; $(MV) HTTPClient.tar HTTPClient
	gzip HTTPClient.tar


### Interface Dependencies

HTTPConnection.class \
HTTPResponse.class \
HttpOutputStream.class \
Response.class \
StreamDemultiplexor.class \
AuthorizationInfo.class \
SocksClient.class \
AuthorizationModule.class \
ContentEncodingModule.class \
ContentMD5Module.class \
CookieModule.class \
DefaultModule.class \
RedirectionModule.class \
RetryModule.class \
TransferEncodingModule.class \
HttpURLConnection.class : GlobalConstants.class

Request.class \
HTTPClientModule.class \
CookiePolicyHandler.class \
AuthorizationHandler.class : RoRequest.class

Response.class \
HTTPClientModule.class \
CookiePolicyHandler.class \
AuthorizationHandler.class : RoResponse.class

HTTPConnection.class \
HTTPResponse.class \
RetryModule.class \
CookieModule.class \
RedirectionModule.class \
AuthorizationModule.class \
ContentMD5Module.class \
TransferEncodingModule.class \
ContentEncodingModule.class \
DefaultModule.class \
HTTPClientModuleAdapter.class : HTTPClientModule.class

HTTPClientModule.class : HTTPClientModuleConstants.class

AuthorizationModule.class \
AuthorizationInfo.class \
DefaultAuthHandler.class : AuthorizationHandler.class

DefaultAuthHandler.class : AuthorizationPrompter.class

CookieModule.class : CookiePolicyHandler.class

ContentMD5Module.class \
MD5InputStream.class \
AuthorizationInfo.class : HashVerifier.class

HttpOutputStream.class : HttpOutputStreamFilter.class

Codecs.class : FilenameMangler.class

