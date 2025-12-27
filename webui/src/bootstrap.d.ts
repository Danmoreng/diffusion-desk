declare module 'bootstrap' {
    export class Modal {
        constructor(element: HTMLElement, options?: any);
        show(): void;
        hide(): void;
        dispose(): void;
        static getInstance(element: HTMLElement): Modal | null;
        static getOrCreateInstance(element: HTMLElement, options?: any): Modal;
    }
    export class Carousel {
        constructor(element: HTMLElement, options?: any);
        to(index: number): void;
        next(): void;
        prev(): void;
        pause(): void;
        cycle(): void;
        static getInstance(element: HTMLElement): Carousel | null;
        static getOrCreateInstance(element: HTMLElement, options?: any): Carousel;
    }
}
