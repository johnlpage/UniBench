package com.mongodb.solcon;

import com.mongodb.MongoClientSettings;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import org.bson.*;
import org.bson.codecs.Codec;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.io.BasicOutputBuffer;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentFactory {
  private static final Logger logger = LoggerFactory.getLogger(DocumentFactory.class);
  final int MEANSTRINGLENGTH = 32;
  final int SAMPLESTRINGLENGTH = 1024 * 1024;
  String bigrandomString;
  String idType;
  int docsizeBytes;
  int maxFieldsPerObject;
  long threadNo;
  HashMap<Integer, Integer> map = new HashMap<>();
  Random random;

  public DocumentFactory(long threadNo, String idType, int docsizeBytes, int maxFieldsPerObject) {
    this.idType = idType;
    this.docsizeBytes = docsizeBytes;
    this.maxFieldsPerObject = maxFieldsPerObject;
    this.threadNo = threadNo;
    this.random = new Random(threadNo);
    bigrandomString = Utils.BigRandomText(SAMPLESTRINGLENGTH);
  }

  private static byte[] asBytes(UUID uuid) {
    long mostSignificantBits = uuid.getMostSignificantBits();
    long leastSignificantBits = uuid.getLeastSignificantBits();
    byte[] bytes = new byte[16];

    for (int i = 0; i < 8; i++) {
      bytes[i] = (byte) (mostSignificantBits >>> (8 * (7 - i)));
      bytes[8 + i] = (byte) (leastSignificantBits >>> (8 * (7 - i)));
    }
    return bytes;
  }

  public RawBsonDocument createDocument() {
    return createDocument(null);
  }

  public RawBsonDocument createDocument(Document extraFields) {

    BasicOutputBuffer buffer = new BasicOutputBuffer();
    BsonWriter writer = new BsonBinaryWriter(buffer);
    int fNo = 1;

    writer.writeStartDocument();
    if (idType != null && !(extraFields != null && extraFields.containsKey("_id"))) {

      switch (idType) {
        case "UUID":
          UUID uuid = UUID.randomUUID();
          BsonBinary bsonBinary = new BsonBinary(BsonBinarySubType.UUID_STANDARD, asBytes(uuid));
          writer.writeBinaryData("_id", bsonBinary);
          break;
        case "BUSINESS_ID":
          int cust = random.nextInt(20000);
          int custOneUp = map.getOrDefault(cust, 0);
          map.put(cust, custOneUp + 1);
          String busId = String.format("ACC%05d_%06x%03x", cust, custOneUp, threadNo);
          writer.writeString("_id", busId);
          break;
        case "OBJECT_ID":
          writer.writeObjectId("_id", new ObjectId());
          break;
        default:
          logger.error("Unknown idType: " + idType);
          writer.writeString("_id", "ERROR");
          break;
      }
    }

    if (extraFields != null) {
      // Codec registry that knows how to encode all default types
      CodecRegistry registry = MongoClientSettings.getDefaultCodecRegistry();

      EncoderContext ctx = EncoderContext.builder().build();

      // Append each entry from Document with the proper type
      for (var entry : extraFields.entrySet()) {
        writer.writeName(entry.getKey());
        @SuppressWarnings("unchecked")
        Codec<Object> codec = (Codec<Object>) registry.get(entry.getValue().getClass());
        codec.encode(writer, entry.getValue(), ctx);
      }
    }

    while (buffer.size() < docsizeBytes) {

      writer.writeInt32("intfield" + fNo, random.nextInt(100_000));
      writer.writeDateTime(
          ("datefield" + fNo), 1754917200011L + random.nextLong(10_000_000_000L)); // TODO Bound
      // these to
      // sensible values
      int stringLength = random.nextInt(MEANSTRINGLENGTH) + 1;
      int offset = random.nextInt(SAMPLESTRINGLENGTH - MEANSTRINGLENGTH);
      String example = bigrandomString.substring(offset, offset + stringLength);
      writer.writeString("stringfield" + fNo, example);
      fNo++;
    }
    writer.writeEndDocument();

    return new RawBsonDocument(buffer.toByteArray());
  }
}
