/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.facade;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.Interfaces;
import java.lang.classfile.Signature;
import java.lang.classfile.Signature.ArrayTypeSig;
import java.lang.classfile.Signature.BaseTypeSig;
import java.lang.classfile.Signature.ClassTypeSig;
import java.lang.classfile.Signature.TypeArg;
import java.lang.classfile.Signature.TypeParam;
import java.lang.classfile.Signature.TypeVarSig;
import java.lang.classfile.Superclass;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class Transformer implements ClassTransform {
    private final List<Config.Entry> config;
    private Superclass superclass;
    private Interfaces interfaces;
    private SignatureAttribute signature;
    private InnerClassesAttribute inners;

    Transformer(List<Config.Entry> config) {
        this.config = config;
    }

    @Override
    public void accept(ClassBuilder builder, ClassElement element) {
        switch (element) {
            case Interfaces intf:
                this.interfaces = intf;
                break;
            case SignatureAttribute sig:
                this.signature = sig;
                break;
            case InnerClassesAttribute inners:
                this.inners = inners;
                break;
            case Superclass sup:
                this.superclass = sup;
            default:
                builder.with(element);
        }
    }

    @Override
    public void atEnd(ClassBuilder builder) {
        var infs = new ArrayList<ClassDesc>();
        if (this.interfaces != null) {
            for (var inf : this.interfaces.interfaces())
                infs.add(inf.asSymbol());
        }

        var sig = this.signature == null ? null : new MutableSignature(this.signature.asClassSignature());

        for (var entry : this.config) {
            var idx = entry.value().indexOf('<');
            if (idx == -1) {
                var desc = ClassDesc.ofInternalName(entry.value());
                infs.add(desc);
                if (sig != null)
                    sig.superinterfaceSignatures.add(ClassTypeSig.of(desc));
            } else {
                if (sig == null)
                    sig = new MutableSignature(this.superclass, infs);

                var internal = entry.value().substring(0, idx);
                var desc = ClassDesc.ofInternalName(internal);
                infs.add(desc);
                try {
                    var parsed = (ClassTypeSig)Signature.parseFrom('L' + entry.value() + ';');
                    var invalid = sig.validate(parsed);
                    if (invalid != null)
                        throw new IllegalStateException("Invalid Interface " + entry.value() + " - References missing Type Parameter " + invalid);
                    sig.superinterfaceSignatures.add(parsed);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Failed to parse entry signature: " + entry.value(), e);
                }
            }
        }


        if (!infs.isEmpty())
            builder.with(Interfaces.ofSymbols(infs));

        if (sig != null)
            builder.with(sig.toAttr());
        else if (this.signature != null)
            builder.with(this.signature);




        if (this.inners != null)
            builder.with(this.inners);
    }

    private static class MutableSignature {
        private final List<TypeParam> typeParameters;
        private final Set<String> types;
        private final ClassTypeSig superclassSignature;
        private final List<ClassTypeSig> superinterfaceSignatures;

        private MutableSignature(ClassSignature cls) {
            this.typeParameters = cls.typeParameters();
            this.types = cls.typeParameters().stream().map(TypeParam::identifier).collect(Collectors.toSet());
            this.superclassSignature = cls.superclassSignature();
            this.superinterfaceSignatures = new ArrayList<>(cls.superinterfaceSignatures());
        }

        private MutableSignature(Superclass supCls, List<ClassDesc> infs) {
            this.typeParameters = List.of();
            this.types = Set.of();
            this.superclassSignature = ClassTypeSig.of(supCls.superclassEntry().asSymbol());
            this.superinterfaceSignatures = new ArrayList<>(infs.size());
            for (var inf : infs)
                this.superinterfaceSignatures.add(ClassTypeSig.of(inf));
        }

        String validate(TypeArg type) {
            if (type instanceof TypeArg.Bounded bound)
                return validate(bound.boundType());
            return null;
        }

        String validate(Signature sig) {
            switch (sig) {
                case BaseTypeSig _:
                    return null;
                case ArrayTypeSig array:
                    return validate(array.componentSignature());
                case ClassTypeSig cls:
                    for (var arg : cls.typeArgs()) {
                        var ret = validate(arg);
                        if (ret != null)
                            return ret;
                    }
                    return null;
                case TypeVarSig type:
                    if (!this.types.contains(type.identifier()))
                        return type.identifier();
            }

            return null;
        }

        SignatureAttribute toAttr() {
            return SignatureAttribute.of(
                ClassSignature.of(
                    typeParameters, superclassSignature, superinterfaceSignatures.toArray(ClassTypeSig[]::new)
                )
            );
        }
    }
}
