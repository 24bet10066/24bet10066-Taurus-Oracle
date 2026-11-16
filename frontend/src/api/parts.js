import client from './client';

export const partsApi = {
  list:     (params = {}) => client.get('/api/v1/parts', { params }),
  lowStock: ()            => client.get('/api/v1/parts/low-stock'),
  get:      (id)          => client.get(`/api/v1/parts/${id}`),
  credit:   (techId)      => client.get(`/api/v1/parts/credit/${techId}`),
  search:   (q)          => client.get('/api/v1/parts/search', { params: { q, limit: 10 } }),
  sell:     (data)       => client.post('/api/v1/parts/sales', data),
};
