import { ApolloServer } from '@apollo/server';
import { startStandaloneServer } from '@apollo/server/standalone';
import { buildSubgraphSchema } from '@apollo/subgraph';
import gql from 'graphql-tag';
import axios from 'axios';

const MONOLITH_API_URL = process.env.MONOLITH_API_URL || 'http://localhost:8080';

const typeDefs = gql`
  type Hotel @key(fields: "id") {
    id: ID!
    name: String
    city: String
    stars: Int
  }

  type Query {
    hotelsByIds(ids: [ID!]!): [Hotel]
  }
`;

const mapHotel = (hotelFromMonolith) => {
  if (!hotelFromMonolith) return null;
  return {
    id: hotelFromMonolith.id,
    name: hotelFromMonolith.name || hotelFromMonolith.description?.substring(0, 30) || `Hotel ${hotelFromMonolith.id}`,
    city: hotelFromMonolith.city || 'Unknown',
    stars: hotelFromMonolith.rating ? Math.round(hotelFromMonolith.rating) : 3,
  };
};

const resolvers = {
  Hotel: {
    __resolveReference: async ({ id }) => {
        try {
          const response = await axios.get(`${MONOLITH_API_URL}/api/hotels/${id}`);
          return mapHotel(response.data);
        } catch (error) {
          if (error.response && error.response.status === 404)
            return null;
          console.error(`Error fetching hotel ${id}:`, error.message);
          return null;
        }
    }
  },
  Query: {
    hotelsByIds: async (_, { ids }) => {
      try {
          const requests = ids.map(id =>
            axios.get(`${MONOLITH_API_URL}/api/hotels/${id}`)
              .then(response => mapHotel(response.data))
              .catch(() => null)
          );
          const results = await Promise.all(requests);
          return results.filter(hotel => hotel !== null);
        } catch (error) {
          console.error('Error fetching hotels by ids:', error.message);
          return [];
        }
    },
  },
};

const server = new ApolloServer({
  schema: buildSubgraphSchema([{ typeDefs, resolvers }]),
});

startStandaloneServer(server, {
  listen: { port: 4002 },
}).then(() => {
  console.log('✅ Hotel subgraph ready at http://localhost:4002/');
});
