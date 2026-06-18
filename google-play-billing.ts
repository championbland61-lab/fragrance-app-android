import { registerPlugin } from '@capacitor/core';

export interface Product {
  productId: string;
  title: string;
  description: string;
  price: string;
  priceCurrencyCode: string;
}

export interface PurchaseResult {
  success: boolean;
}

export interface RestoredPurchase {
  purchaseToken: string;
  products: string;
}

export interface BillingPlugin {
  getProducts(options: { productIds: string[] }): Promise<{ products: Product[] }>;
  purchase(options: { productId: string }): Promise<PurchaseResult>;
  restorePurchases(): Promise<{ purchases: RestoredPurchase[] }>;
}

const Billing = registerPlugin<BillingPlugin>('Billing');

export { Billing };
