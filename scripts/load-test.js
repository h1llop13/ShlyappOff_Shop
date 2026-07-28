import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 50,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate==0'],
    'http_req_duration{endpoint:home}': ['p(95)<300'],
    'http_req_duration{endpoint:catalog}': ['p(95)<300'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';

export default function () {
  const home = http.get(`${baseUrl}/`, { tags: { endpoint: 'home' } });
  check(home, { 'home: HTTP 200': (response) => response.status === 200 });

  const catalog = http.get(`${baseUrl}/catalog`, { tags: { endpoint: 'catalog' } });
  check(catalog, { 'catalog: HTTP 200': (response) => response.status === 200 });
  sleep(0.3);
}
