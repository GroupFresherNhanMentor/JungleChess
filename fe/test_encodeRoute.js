const { encodeRoute, encodeRoutes } = require('rsocket-core');
console.log(encodeRoute("room.create").toString('hex'));
