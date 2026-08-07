const { MESSAGE_RSOCKET_ROUTING, encodeCompositeMetadata } = require('rsocket-core');
const route = "room.create";
const routeBytes = Buffer.from(route, 'utf8');
const routingData = Buffer.alloc(1 + routeBytes.length);
routingData.writeUInt8(routeBytes.length, 0);
routeBytes.copy(routingData, 1);
try {
  const metadata = encodeCompositeMetadata([[MESSAGE_RSOCKET_ROUTING, routingData]]);
  console.log("Metadata encoded successfully. Length:", metadata.length);
  console.log("Metadata hex:", metadata.toString('hex'));
} catch (e) {
  console.error("Error encoding:", e);
}
