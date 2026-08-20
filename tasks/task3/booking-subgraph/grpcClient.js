import grpc from '@grpc/grpc-js';
import protoLoader from '@grpc/proto-loader';
import path from 'path';
import { fileURLToPath } from 'url';

const BOOKING_SERVICE_GRPC_SOCKET = process.env.BOOKING_SERVICE_GRPC_SOCKET || 'booking-service:9090';

const PROTO_PATH = path.join(__dirname, 'booking.proto');
const packageDefinition = protoLoader.loadSync(PROTO_PATH, {
  keepCase: true,
  longs: String,
  enums: String,
  defaults: true,
  oneofs: true,
});
const protoDescriptor = grpc.loadPackageDefinition(packageDefinition);
const bookingProto = protoDescriptor.booking;

const client = new bookingProto.BookingService(BOOKING_SERVICE_GRPC_SOCKET, grpc.credentials.createInsecure());
const { promisify } = require('util');
const listBookings = promisify(client.listBookings).bind(client);

export { listBookings };